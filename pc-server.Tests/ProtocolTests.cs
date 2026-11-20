using System.Text;
using System.Text.Json;
using XvcPcServer;
using Xunit;

public class ProtocolTests
{
    [Fact]
    public void ParsesStateAndInvertsY()
    {
        Assert.True(Protocol.TryParseState(
            "{\"t\":\"state\",\"seq\":5,\"btn\":129,\"lx\":1000,\"ly\":2000,\"rx\":-3,\"ry\":-4,\"lt\":10,\"rt\":255}",
            out var s, out var err));
        Assert.Null(err);
        Assert.Equal(new PadState(Btn.A | Btn.START, 1000, -2000, -3, 4, 10, 255), s);
    }

    [Fact]
    public void ExtremeValuesAreClampedNotWrapped()
    {
        // ly = -32768 used to become (short)32768 = -32768 again after inverting: stick stuck the wrong way.
        Assert.True(Protocol.TryParseState(
            "{\"t\":\"state\",\"btn\":0,\"lx\":99999,\"ly\":-32768,\"rx\":-99999,\"ry\":32767,\"lt\":-5,\"rt\":300}",
            out var s, out _));
        Assert.Equal(short.MaxValue, s.Lx);
        Assert.Equal(short.MaxValue, s.Ly);
        Assert.Equal(short.MinValue, s.Rx);
        Assert.Equal(-32767, s.Ry);
        Assert.Equal(0, s.Lt);
        Assert.Equal(255, s.Rt);
    }

    [Fact]
    public void DigitalTriggerBitsPressTheTriggerFully()
    {
        Assert.True(Protocol.TryParseState($"{{\"t\":\"state\",\"btn\":{Btn.LT | Btn.RT | Btn.B}}}", out var s, out _));
        Assert.Equal(255, s.Lt);
        Assert.Equal(255, s.Rt);
        Assert.Equal(Btn.B, s.Buttons); // trigger bits are not Xbox buttons
    }

    [Theory]
    [InlineData("{\"t\":\"ping\"}", false)]
    [InlineData("", false)]
    [InlineData("[1,2]", true)]
    [InlineData("{not json", true)]
    [InlineData("{\"t\":5}", false)]
    public void NonStateLinesAreRejected(string line, bool isError)
    {
        Assert.False(Protocol.TryParseState(line, out _, out var err));
        Assert.Equal(isError, err != null);
    }

    [Fact]
    public void OversizedLineIsRejected()
    {
        Assert.False(Protocol.TryParseState(new string(' ', Protocol.MaxLineLength + 1) + "{}", out _, out var err));
        Assert.NotNull(err);
    }

    [Fact]
    public void MissingFieldsDefaultToNeutral()
    {
        Assert.True(Protocol.TryParseState("{\"t\":\"state\"}", out var s, out _));
        Assert.Equal(PadState.Neutral, s);
    }

    [Fact]
    public void DiscoveryReplyEscapesTheMachineName()
    {
        var json = Protocol.DiscoveryReply("192.168.1.5", 39500, "PC \"gamer\" \\ ñ");
        using var doc = JsonDocument.Parse(json);
        Assert.Equal("xvc", doc.RootElement.GetProperty("t").GetString());
        Assert.Equal("PC \"gamer\" \\ ñ", doc.RootElement.GetProperty("name").GetString());
        Assert.Equal(39500, doc.RootElement.GetProperty("port").GetInt32());
    }
}

public class LineReaderTests
{
    [Fact]
    public async Task SplitsLinesAcrossReadsAndHandlesCrLf()
    {
        var reader = new LineReader(new MemoryStream(Encoding.UTF8.GetBytes("a\r\nbé\n\nc")));
        var t = TimeSpan.FromSeconds(1);
        Assert.Equal("a", await reader.ReadLineAsync(t, default));
        Assert.Equal("bé", await reader.ReadLineAsync(t, default));
        Assert.Equal("", await reader.ReadLineAsync(t, default));
        Assert.Null(await reader.ReadLineAsync(t, default)); // "c" without newline at EOF is dropped
    }

    [Fact]
    public async Task EndlessLineIsRejected()
    {
        var reader = new LineReader(new MemoryStream(new byte[Protocol.MaxLineLength * 3]));
        await Assert.ThrowsAsync<InvalidDataException>(() => reader.ReadLineAsync(TimeSpan.FromSeconds(1), default));
    }

    [Fact]
    public async Task SilentPeerTimesOut()
    {
        using var server = new System.Net.Sockets.TcpListener(System.Net.IPAddress.Loopback, 0);
        server.Start();
        using var client = new System.Net.Sockets.TcpClient();
        await client.ConnectAsync(System.Net.IPAddress.Loopback, ((System.Net.IPEndPoint)server.LocalEndpoint).Port);
        using var accepted = await server.AcceptTcpClientAsync();
        var reader = new LineReader(accepted.GetStream());
        await Assert.ThrowsAsync<TimeoutException>(() => reader.ReadLineAsync(TimeSpan.FromMilliseconds(200), default));
    }
}
