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

// Helpers locales
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
string Escape(string s) => s.Replace("\\", "\\\\").Replace("\"", "\\\"");

// Args y binding
string? hostArg = null;
int port = 39500;
if (args.Length >= 1) { if (int.TryParse(args[0], out var p1)) port = p1; else hostArg = args[0]; }
if (args.Length >= 2 && int.TryParse(args[1], out var p2)) port = p2;

var primary = GetPrimaryIPv4();
var bindAddress = hostArg != null ? ResolveBindAddress(hostArg) : (primary ?? IPAddress.Any);
var host = hostArg ?? bindAddress.ToString();

Console.WriteLine($"XVC PC Server escuchando en {host}:{port}");
Console.WriteLine("IPs locales detectadas (usa una en Android):");
foreach (var it in GetLocalIPv4WithIf()) Console.WriteLine($" - {it.ip} ({it.ifName})");

// ViGEm
using var vigem = new ViGEmClient();
var x360 = vigem.CreateXbox360Controller();
x360.Connect();
Neutral();

// TCP
Console.WriteLine($"Binding en: {bindAddress}:{port}");
var listener = new TcpListener(bindAddress, port);
listener.Start();
Console.WriteLine($"Sugerencia firewall (PowerShell admin): netsh advfirewall firewall add rule name=\"XVC {port}\" dir=in action=allow protocol=TCP localport={port}");

// UDP discovery (39501)
const int discoveryPort = 39501;
Console.WriteLine($"Discovery UDP en 0.0.0.0:{discoveryPort}");
_ = Task.Run(async () =>
{
    try
    {
        using var udp = new UdpClient(new IPEndPoint(IPAddress.Any, discoveryPort));
        udp.EnableBroadcast = true;
        while (true)
        {
            var res = await udp.ReceiveAsync();
            var msg = Encoding.UTF8.GetString(res.Buffer);
            if (!msg.StartsWith("XVC_DISCOVER")) continue;

            var advIp = bindAddress.Equals(IPAddress.Any) ? (primary?.ToString() ?? "0.0.0.0") : bindAddress.ToString();
            var payload = $"{{\"t\":\"xvc\",\"ip\":\"{advIp}\",\"port\":{port},\"name\":\"{Escape(Environment.MachineName)}\"}}";
            var bytes = Encoding.UTF8.GetBytes(payload);
            await udp.SendAsync(bytes, bytes.Length, res.RemoteEndPoint);
        }
    }
    catch (Exception ex)
    {
        Console.WriteLine($"Discovery UDP error: {ex.Message}");
    }
});

// Loop
Console.CancelKeyPress += (_, e) => { e.Cancel = true; Environment.Exit(0); };

while (true)
{
    using var client = await listener.AcceptTcpClientAsync();
    try { client.Client.NoDelay = true; } catch { }
    Console.WriteLine($"Cliente conectado: {client.Client.RemoteEndPoint}");
    try
    {
        using var ns = client.GetStream();
        using var reader = new StreamReader(ns, new UTF8Encoding(false), detectEncodingFromByteOrderMarks: false, bufferSize: 8192, leaveOpen: false);
        string? line;
        while ((line = await reader.ReadLineAsync()) != null)
        {
            if (line.Length == 0) continue;
            try
            {
                using var doc = JsonDocument.Parse(line);
                var root = doc.RootElement;
                if (!root.TryGetProperty("t", out var tProp) || tProp.GetString() != "state") continue;

                var btn = root.GetProperty("btn").GetInt32();
                var lx = (short)root.GetProperty("lx").GetInt32();
                var ly = (short)root.GetProperty("ly").GetInt32();
                var rx = (short)root.GetProperty("rx").GetInt32();
                var ry = (short)root.GetProperty("ry").GetInt32();
                var lt = (byte)root.GetProperty("lt").GetInt32();
                var rt = (byte)root.GetProperty("rt").GetInt32();

                Apply(btn, lx, ly, rx, ry, lt, rt);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"JSON inválido: {ex.Message}");
            }
        }
    }
    catch (Exception ex)
    {
        Console.WriteLine($"Error de conexión: {ex.Message}");
    }
    finally
    {
        Console.WriteLine("Cliente desconectado");
        Neutral();
    }
}

// Funciones locales de entrada
void Apply(int btn, short lx, short ly, short rx, short ry, byte lt, byte rt)
{
    bool Has(int m) => (btn & m) != 0;
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
    x360.SetAxisValue(Xbox360Axis.LeftThumbX, lx);
    x360.SetAxisValue(Xbox360Axis.LeftThumbY, (short)-ly); // invertido
    x360.SetAxisValue(Xbox360Axis.RightThumbX, rx);
    x360.SetAxisValue(Xbox360Axis.RightThumbY, (short)-ry); // invertido
    x360.SetSliderValue(Xbox360Slider.LeftTrigger, lt);
    x360.SetSliderValue(Xbox360Slider.RightTrigger, rt);
}
void Neutral() => Apply(0, 0, 0, 0, 0, 0, 0);

// Tipos después de las instrucciones de nivel superior
static class Btn
{
    public const int A = 1 << 0;
    public const int B = 1 << 1;
    public const int X = 1 << 2;
    public const int Y = 1 << 3;
    public const int LB = 1 << 4;
    public const int RB = 1 << 5;
    public const int BACK = 1 << 6;
    public const int START = 1 << 7;
    public const int LS = 1 << 8;
    public const int RS = 1 << 9;
    public const int GUIDE = 1 << 10;
    public const int DPAD_UP = 1 << 11;
    public const int DPAD_DOWN = 1 << 12;
    public const int DPAD_LEFT = 1 << 13;
    public const int DPAD_RIGHT = 1 << 14;
}
