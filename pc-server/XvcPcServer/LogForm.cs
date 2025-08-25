using System;
using System.Windows.Forms;

namespace XvcPcServer
{
    public class LogForm : Form
    {
        private readonly TextBox logBox;
        private XvcServer? server;
        public LogForm()
        {
            this.Text = "XvcPcServer - Log";
            this.Width = 600;
            this.Height = 400;
            logBox = new TextBox
            {
                Multiline = true,
                Dock = DockStyle.Fill,
                ReadOnly = true,
                ScrollBars = ScrollBars.Vertical,
                Font = new System.Drawing.Font("Consolas", 10),
            };
            this.Controls.Add(logBox);
            this.Load += LogForm_Load;
        }
        private void LogForm_Load(object? sender, EventArgs e)
        {
            server = new XvcServer(AppendLog);
            server.Start();
        }
        public void StopServer()
        {
            try { server?.Stop(); } catch { }
        }
        public void AppendLog(string text)
        {
            if (InvokeRequired)
            {
                Invoke(new Action<string>(AppendLog), text);
                return;
            }
            logBox.AppendText(text + Environment.NewLine);
        }
    }
}
