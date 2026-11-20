// Lógica del servidor movida a clase para integración con WinForms
using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Net.NetworkInformation;
using System.Linq;
using System.Threading.Tasks;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;
using System.IO;
using System.Threading;
using System.Diagnostics;

namespace XvcPcServer
{
    public class XvcServer
    {
        private readonly Action<string> log;
        private CancellationTokenSource? cts;
    private TcpListener? listener;
        public XvcServer(Action<string> logAction)
        {
            log = logAction;
        }
        public void Start(string? hostArg = null, int port = 39500)
        {
            cts = new CancellationTokenSource();
            Task.Run(() => RunServer(hostArg, port, cts.Token));
        }
        public void Stop()
        {
            try { cts?.Cancel(); } catch { }
            try { listener?.Stop(); } catch { }
            try { x360?.Disconnect(); } catch { }
            try { x360 = null; } catch { }
            try { vigem?.Dispose(); } catch { }
            try { vigem = null; } catch { }
        }
        private async Task RunServer(string? hostArg, int port, CancellationToken token)
        {
            try
            {
                bool IsPrivateIPv4(IPAddress ip)
                {
                    if (ip.AddressFamily != AddressFamily.InterNetwork) return false;
                    var b = ip.GetAddressBytes();
                    return b[0] == 10 || (b[0] == 172 && b[1] >= 16 && b[1] <= 31) || (b[0] == 192 && b[1] == 168);
                }
                IPAddress? GetPrimaryIPv4()
                {
                    IPAddress? candidate = null;
                    foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
                    {
                        if (ni.OperationalStatus != OperationalStatus.Up) continue;
                        if (ni.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel) continue;
                        var name = (ni.Name + " " + ni.Description).ToLowerInvariant();
                        if (name.Contains("virtual") || name.Contains("vmware") || name.Contains("hyper-v") || name.Contains("virtualbox") || name.Contains("tailscale")) continue;
                        var props = ni.GetIPProperties();
                        var hasGw = props.GatewayAddresses.Any(g => g?.Address is { AddressFamily: AddressFamily.InterNetwork } && !Equals(g.Address, IPAddress.Any));
                        var ipv4s = props.UnicastAddresses.Where(u => u.Address.AddressFamily == AddressFamily.InterNetwork).Select(u => u.Address).ToList();
                        if (ipv4s.Count == 0) continue;
                        var priv = ipv4s.FirstOrDefault(IsPrivateIPv4);
                        var pick = priv ?? ipv4s.First();
                        if (hasGw && IsPrivateIPv4(pick)) return pick;
                        candidate ??= pick;
                    }
                    return candidate;
                }
                IPAddress ResolveBindAddress(string h)
                {
                    if (string.IsNullOrWhiteSpace(h) || h == "0.0.0.0" || h == "*") return IPAddress.Any;
                    if (h.Equals("localhost", StringComparison.OrdinalIgnoreCase)) return IPAddress.Loopback;
                    if (IPAddress.TryParse(h, out var ip)) return ip;
                    try
                    {
                        var entry = Dns.GetHostEntry(h);
                        return entry.AddressList.FirstOrDefault(a => a.AddressFamily == AddressFamily.InterNetwork)
                               ?? entry.AddressList.FirstOrDefault()
                               ?? IPAddress.Any;
                    }
                    catch { return IPAddress.Any; }
                }
                IEnumerable<(IPAddress ip, string ifName)> GetLocalIPv4WithIf()
                {
                    foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
                    {
                        if (ni.OperationalStatus != OperationalStatus.Up) continue;
                        if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                        var props = ni.GetIPProperties();
                        foreach (var ua in props.UnicastAddresses)
                            if (ua.Address.AddressFamily == AddressFamily.InterNetwork)
                                yield return (ua.Address, ni.Name);
                    }
                }

                var primary = GetPrimaryIPv4();
                var bindAddress = hostArg != null ? ResolveBindAddress(hostArg) : (primary ?? IPAddress.Any);
                var host = hostArg ?? bindAddress.ToString();

                log($"XVC PC Server escuchando en {host}:{port}");
                log("IPs locales detectadas (usa una en Android):");
                foreach (var it in GetLocalIPv4WithIf()) log($" - {it.ip} ({it.ifName})");

                vigem = new ViGEmClient();
                x360 = vigem.CreateXbox360Controller();
                x360.Connect();
                Neutral();

                log($"Binding en: {bindAddress}:{port}");
                listener = new TcpListener(bindAddress, port);
                try { listener.Start(); }
                catch (SocketException se) when (se.SocketErrorCode == SocketError.AddressAlreadyInUse)
                {
                    log($"El puerto {port} ya está en uso. Intentando liberar el puerto...");
                    if (TryFreeOwnProcessesOnPort(port))
                    {
                        Thread.Sleep(800);
                        try
                        {
                            listener.Start();
                            log($"Puerto {port} liberado y en uso por esta instancia.");
                        }
                        catch (SocketException)
                        {
                            log($"No se pudo tomar el puerto {port} tras liberar. Abortando.");
                            return;
                        }
                    }
                    else
                    {
                        log($"No se pudo liberar el puerto {port}. Puede estar usado por otra aplicación.");
                        return;
                    }
                }
                log($"Sugerencia firewall (PowerShell admin): netsh advfirewall firewall add rule name=\"XVC {port}\" dir=in action=allow protocol=TCP localport={port}");

                // UDP discovery (39501)
                const int discoveryPort = Protocol.DiscoveryPort;
                log($"Discovery UDP en 0.0.0.0:{discoveryPort}");
                _ = Task.Run(async () =>
                {
                    try
                    {
                        using var udp = new UdpClient(new IPEndPoint(IPAddress.Any, discoveryPort));
                        udp.EnableBroadcast = true;
                        while (!token.IsCancellationRequested)
                        {
                            // Pass the token so Stop() releases port 39501 (it used to stay bound).
                            var res = await udp.ReceiveAsync(token);
                            var msg = Encoding.UTF8.GetString(res.Buffer);
                            if (!msg.StartsWith(Protocol.DiscoveryRequest)) continue;
                            var advIp = bindAddress.Equals(IPAddress.Any) ? (primary?.ToString() ?? "0.0.0.0") : bindAddress.ToString();
                            var payload = Protocol.DiscoveryReply(advIp, port, Environment.MachineName);
                            var bytes = Encoding.UTF8.GetBytes(payload);
                            await udp.SendAsync(bytes, bytes.Length, res.RemoteEndPoint);
                        }
                    }
                    catch (OperationCanceledException) { }
                    catch (Exception ex)
                    {
                        log($"Discovery UDP error: {ex.Message}");
                    }
                });

                while (!token.IsCancellationRequested)
                {
                    if (listener == null) break;
                    using var client = await listener.AcceptTcpClientAsync(token);
                    try { client.Client.NoDelay = true; } catch { }
                    log($"Cliente conectado: {client.Client.RemoteEndPoint}");
                    try
                    {
                        using var ns = client.GetStream();
                        var reader = new LineReader(ns);
                        int badLines = 0;
                        string? line;
                        // The app sends a ping every 3 s (and the background service re-sends state every 20 ms),
                        // so silence means the phone is gone: release every button instead of
                        // leaving them held until TCP notices, which can take minutes.
                        while ((line = await reader.ReadLineAsync(IdleTimeout, token)) != null)
                        {
                            if (Protocol.TryParseState(line, out var st, out var err))
                            {
                                Apply(st);
                            }
                            else if (err != null && badLines++ < 5)
                            {
                                log($"JSON inválido: {err}");
                            }
                        }
                    }
                    catch (TimeoutException)
                    {
                        log($"Sin datos del cliente en {IdleTimeout.TotalSeconds:0} s; se sueltan todos los botones.");
                    }
                    catch (OperationCanceledException) { }
                    catch (Exception ex)
                    {
                        log($"Error de conexión: {ex.Message}");
                    }
                    finally
                    {
                        log("Cliente desconectado");
                        Neutral();
                    }
                }
            }
            catch (Exception ex)
            {
                log($"Error general: {ex.Message}");
            }
        }
        private bool TryFreeOwnProcessesOnPort(int port)
        {
            try
            {
                var pids = GetPidsForTcpPort(port).ToList();
                if (pids.Count == 0) return false;
                var current = Process.GetCurrentProcess();
                string? exeName = null;
                try { exeName = Path.GetFileName(current.MainModule?.FileName); } catch { exeName = current.ProcessName + ".exe"; }

                bool any = false;
                foreach (var pid in pids)
                {
                    try
                    {
                        if (pid == current.Id) continue;
                        var proc = Process.GetProcessById(pid);
                        string? otherName = null;
                        try { otherName = Path.GetFileName(proc.MainModule?.FileName); } catch { otherName = proc.ProcessName + ".exe"; }
                        if (!string.Equals(otherName, exeName, StringComparison.OrdinalIgnoreCase))
                            continue; // No matamos apps ajenas

                        log($"Terminando instancia previa PID {pid}...");
                        try { proc.CloseMainWindow(); } catch { }
                        if (!proc.WaitForExit(500))
                        {
                            try { proc.Kill(true); } catch { }
                            proc.WaitForExit(1000);
                        }
                        any = true;
                    }
                    catch { }
                }
                // Esperar a que el puerto quede libre
                var sw = Stopwatch.StartNew();
                while (sw.ElapsedMilliseconds < 3000)
                {
                    if (!GetPidsForTcpPort(port).Any()) return any;
                    Thread.Sleep(150);
                }
                return !GetPidsForTcpPort(port).Any();
            }
            catch { return false; }
        }

        private IEnumerable<int> GetPidsForTcpPort(int port)
        {
            var list = new List<int>();
            try
            {
                var psi = new ProcessStartInfo
                {
                    FileName = "netstat",
                    Arguments = "-ano -p TCP",
                    CreateNoWindow = true,
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true
                };
                using var p = Process.Start(psi)!;
                var output = p.StandardOutput.ReadToEnd();
                p.WaitForExit(2000);
                foreach (var raw in output.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries))
                {
                    var line = raw.Trim();
                    if (!line.StartsWith("TCP", StringComparison.OrdinalIgnoreCase)) continue;
                    var parts = System.Text.RegularExpressions.Regex.Split(line, "\\s+").Where(s => s.Length > 0).ToArray();
                    if (parts.Length < 5) continue;
                    var local = parts[1];
                    var state = parts[3];
                    var pidStr = parts[4];
                    if (!(local.EndsWith(":" + port) || local.EndsWith("]:" + port))) continue;
                    if (!(state.IndexOf("LISTEN", StringComparison.OrdinalIgnoreCase) >= 0 || state.IndexOf("ESCUCH", StringComparison.OrdinalIgnoreCase) >= 0)) continue;
                    if (int.TryParse(pidStr, out var pid)) list.Add(pid);
                }
            }
            catch { }
            return list;
        }
        // Funciones locales de entrada
        private ViGEmClient? vigem;
        private IXbox360Controller? x360;
        private void Apply(PadState s)
        {
            if (x360 == null) return;
            var btn = s.Buttons;
            bool Has(int m) => (btn & m) != 0;
            x360.AutoSubmitReport = false;
            x360.SetButtonState(Xbox360Button.A, Has(Btn.A));
            x360.SetButtonState(Xbox360Button.B, Has(Btn.B));
            x360.SetButtonState(Xbox360Button.X, Has(Btn.X));
            x360.SetButtonState(Xbox360Button.Y, Has(Btn.Y));
            x360.SetButtonState(Xbox360Button.LeftShoulder, Has(Btn.LB));
            x360.SetButtonState(Xbox360Button.RightShoulder, Has(Btn.RB));
            x360.SetButtonState(Xbox360Button.Back, Has(Btn.BACK));
            x360.SetButtonState(Xbox360Button.Start, Has(Btn.START));
            x360.SetButtonState(Xbox360Button.LeftThumb, Has(Btn.LS));
            x360.SetButtonState(Xbox360Button.RightThumb, Has(Btn.RS));
            x360.SetButtonState(Xbox360Button.Guide, Has(Btn.GUIDE));
            x360.SetButtonState(Xbox360Button.Up, Has(Btn.DPAD_UP));
            x360.SetButtonState(Xbox360Button.Down, Has(Btn.DPAD_DOWN));
            x360.SetButtonState(Xbox360Button.Left, Has(Btn.DPAD_LEFT));
            x360.SetButtonState(Xbox360Button.Right, Has(Btn.DPAD_RIGHT));
            x360.SetAxisValue(Xbox360Axis.LeftThumbX, s.Lx);
            x360.SetAxisValue(Xbox360Axis.LeftThumbY, s.Ly);
            x360.SetAxisValue(Xbox360Axis.RightThumbX, s.Rx);
            x360.SetAxisValue(Xbox360Axis.RightThumbY, s.Ry);
            x360.SetSliderValue(Xbox360Slider.LeftTrigger, s.Lt);
            x360.SetSliderValue(Xbox360Slider.RightTrigger, s.Rt);
            // One report per message instead of 21 (AutoSubmitReport sent one per setter).
            x360.SubmitReport();
        }
        private void Neutral() => Apply(PadState.Neutral);
        private static readonly TimeSpan IdleTimeout = TimeSpan.FromSeconds(8);
    }
}
