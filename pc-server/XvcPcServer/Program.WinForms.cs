using System;
using System.Threading;
using System.Windows.Forms;

namespace XvcPcServer
{
    static class Program
    {
        [STAThread]
        static void Main()
        {
            using var mutex = new Mutex(true, "Global/XvcPcServerSingleton", out bool isNew);
            if (!isNew)
            {
                MessageBox.Show("XvcPcServer ya está en ejecución.", "XvcPcServer", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new TrayAppContext());
        }
    }
}
