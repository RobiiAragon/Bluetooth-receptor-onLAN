using System;
using System.Drawing;
using System.Windows.Forms;

namespace XvcPcServer
{
    public class TrayAppContext : ApplicationContext
    {
        private readonly NotifyIcon trayIcon;
        private readonly Form logForm;

        public TrayAppContext()
        {
            logForm = new LogForm();
            logForm.FormClosing += (s, e) => {
                e.Cancel = true;
                logForm.Hide();
            };

            trayIcon = new NotifyIcon()
            {
                Icon = SystemIcons.Application,
                ContextMenuStrip = new ContextMenuStrip(),
                Text = "XvcPcServer",
                Visible = true
            };
            trayIcon.ContextMenuStrip.Items.Add("Mostrar ventana", null, (s, e) => ShowLogForm());
            trayIcon.ContextMenuStrip.Items.Add("Salir", null, (s, e) => Exit());
            trayIcon.DoubleClick += (s, e) => ShowLogForm();
            ShowLogForm();
        }

        private void ShowLogForm()
        {
            if (!logForm.Visible)
                logForm.Show();
            logForm.WindowState = FormWindowState.Normal;
            logForm.BringToFront();
        }

        private void Exit()
        {
            trayIcon.Visible = false;
            if (logForm is LogForm lf)
            {
                var stop = lf.GetType().GetMethod("StopServer", System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.NonPublic);
                stop?.Invoke(lf, null);
            }
            Application.Exit();
        }
    }
}
