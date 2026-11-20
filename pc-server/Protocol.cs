using System;
using System.IO;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace XvcPcServer
{
    /// <summary>One controller snapshot sent by the Android app as a JSON line.</summary>
    public readonly record struct PadState(int Buttons, short Lx, short Ly, short Rx, short Ry, byte Lt, byte Rt)
    {
        public static readonly PadState Neutral = new(0, 0, 0, 0, 0, 0, 0);
    }

    /// <summary>
    /// Wire protocol shared with android-app (net/ControllerClient.kt):
    /// newline-delimited JSON, {"t":"state","btn":..,"lx":..,"ly":..,"rx":..,"ry":..,"lt":..,"rt":..}
    /// plus {"t":"ping"} keepalives every 3 s.
    /// </summary>
    public static class Protocol
    {
        public const int DefaultPort = 39500;
        public const int DiscoveryPort = 39501;
        public const string DiscoveryRequest = "XVC_DISCOVER";
        /// <summary>A state line is ~100 bytes; anything far longer is garbage or abuse.</summary>
        public const int MaxLineLength = 1024;

        /// <summary>
        /// Parses one line. Returns true for a state message. Pings and unknown types return
        /// false without error. Values are clamped instead of wrapping around (a bad value
        /// used to turn a full stick deflection into the opposite direction).
        /// </summary>
        public static bool TryParseState(string line, out PadState state, out string? error)
        {
            state = default;
            error = null;
            if (string.IsNullOrWhiteSpace(line)) return false;
            if (line.Length > MaxLineLength) { error = "line too long"; return false; }
            try
            {
                using var doc = JsonDocument.Parse(line);
                var root = doc.RootElement;
                if (root.ValueKind != JsonValueKind.Object) { error = "not an object"; return false; }
                if (!root.TryGetProperty("t", out var t) || t.ValueKind != JsonValueKind.String || t.GetString() != "state")
                    return false;

                int Int(string name)
                {
                    if (!root.TryGetProperty(name, out var p) || p.ValueKind != JsonValueKind.Number) return 0;
                    return p.TryGetInt64(out var v) ? (int)Math.Clamp(v, int.MinValue, int.MaxValue)
                         : (int)Math.Clamp(p.GetDouble(), int.MinValue, int.MaxValue);
                }

                var btn = Int("btn");
                byte lt = (byte)Math.Clamp(Int("lt"), 0, 255);
                byte rt = (byte)Math.Clamp(Int("rt"), 0, 255);
                // Digital triggers (e.g. Switch Pro ZL/ZR) arrive as button bits.
                if ((btn & Btn.LT) != 0) lt = 255;
                if ((btn & Btn.RT) != 0) rt = 255;
                state = new PadState(
                    btn & Btn.AllXbox,
                    Axis(Int("lx")),
                    // Android's Y axis points down, XInput's points up.
                    Axis(-(long)Int("ly")),
                    Axis(Int("rx")),
                    Axis(-(long)Int("ry")),
                    lt, rt);
                return true;
            }
            catch (JsonException ex)
            {
                error = ex.Message;
                return false;
            }
        }

        public static short Axis(long v) => (short)Math.Clamp(v, short.MinValue, short.MaxValue);

        public static string DiscoveryReply(string ip, int port, string machineName) =>
            JsonSerializer.Serialize(new { t = "xvc", ip, port, name = machineName });
    }

    /// <summary>
    /// Reads newline-delimited UTF-8 lines with a hard length limit and an idle timeout,
    /// so a client that stops sending (Wi-Fi drop, phone asleep) or never sends a newline
    /// can't keep the virtual pad stuck or grow memory without bound.
    /// </summary>
    public sealed class LineReader
    {
        private readonly Stream stream;
        private readonly byte[] buffer = new byte[4096];
        private readonly MemoryStream pending = new();

        public LineReader(Stream stream) => this.stream = stream;

        /// <summary>Next line, or null when the peer closed the connection.</summary>
        /// <exception cref="TimeoutException">No data for <paramref name="idleTimeout"/>.</exception>
        /// <exception cref="InvalidDataException">A line exceeded <see cref="Protocol.MaxLineLength"/>.</exception>
        public async Task<string?> ReadLineAsync(TimeSpan idleTimeout, CancellationToken token)
        {
            while (true)
            {
                var line = TryTakeLine();
                if (line != null) return line;
                if (pending.Length > Protocol.MaxLineLength)
                    throw new InvalidDataException("line too long");

                using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
                timeout.CancelAfter(idleTimeout);
                int n;
                try
                {
                    n = await stream.ReadAsync(buffer, timeout.Token).ConfigureAwait(false);
                }
                catch (OperationCanceledException) when (!token.IsCancellationRequested)
                {
                    throw new TimeoutException($"no data for {idleTimeout.TotalSeconds:0.#} s");
                }
                if (n == 0) return null;
                pending.Write(buffer, 0, n);
            }
        }

        private string? TryTakeLine()
        {
            var data = pending.GetBuffer();
            var len = (int)pending.Length;
            var idx = Array.IndexOf(data, (byte)'\n', 0, len);
            if (idx < 0) return null;
            var end = idx > 0 && data[idx - 1] == (byte)'\r' ? idx - 1 : idx;
            var line = Encoding.UTF8.GetString(data, 0, end);
            var rest = len - idx - 1;
            Buffer.BlockCopy(data, idx + 1, data, 0, rest);
            pending.SetLength(rest);
            return line;
        }
    }

    /// <summary>Button bits. Must match Btn in android-app/.../input/ControllerState.kt.</summary>
    public static class Btn
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
        public const int LT = 1 << 15;
        public const int RT = 1 << 16;
        public const int AllXbox = (1 << 15) - 1;
    }
}
