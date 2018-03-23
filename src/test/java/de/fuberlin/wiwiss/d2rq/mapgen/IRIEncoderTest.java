package de.fuberlin.wiwiss.d2rq.mapgen;

import org.junit.Assert;
import org.junit.Test;

public class IRIEncoderTest {

    @Test
    public void testDontEncodeAlphanumeric() {
        Assert.assertEquals("azAZ09", IRIEncoder.encode("azAZ09"));
    }

    @Test
    public void testDontEncodeSafePunctuation() {
        Assert.assertEquals("-_.~", IRIEncoder.encode("-_.~"));
    }

    @Test
    public void testDontEncodeUnicodeChars() {
        // This is 'LATIN SMALL LETTER A WITH DIAERESIS' (U+00E4)
        Assert.assertEquals("\u00E4", IRIEncoder.encode("\u00E4"));
        // First char to be not encoded
        Assert.assertEquals("\u00A0", IRIEncoder.encode("\u00A0"));
        Assert.assertEquals("\uD7FF", IRIEncoder.encode("\uD7FF"));
        Assert.assertEquals("\uFFEF", IRIEncoder.encode("\uFFEF"));
    }

    @Test
    public void testEncodeGenDelims() {
        Assert.assertEquals("%3A%2F%3F%23%5B%5D%40", IRIEncoder.encode(":/?#[]@"));
    }

    @Test
    public void testEncodeSubDelims() {
        Assert.assertEquals("%21%24%26%27%28%29%2A%2B%2C%3B%3D", IRIEncoder.encode("!$&'()*+,;="));
    }

    @Test
    public void testEncodePercentSign() {
        Assert.assertEquals("%25", IRIEncoder.encode("%"));
    }

    @Test
    public void testEncodeOtherASCIIChars() {
        Assert.assertEquals("%20%22%3C%3E%5C%5E%60%7B%7C%7D", IRIEncoder.encode(" \"<>\\^`{|}"));
    }

    @Test
    public void testEncodeASCIIControlChars() {
        Assert.assertEquals("%00%01%02%03%04%05%06%07%08%09%0A%0B%0C%0D%0E%0F",
                IRIEncoder.encode("\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\u0009\n\u000B\u000C\r\u000E\u000F"));
        Assert.assertEquals("%10%11%12%13%14%15%16%17%18%19%1A%1B%1C%1D%1E%1F",
                IRIEncoder.encode("\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F"));
        Assert.assertEquals("%7F", IRIEncoder.encode("\u007F"));
    }

    @Test
    public void testEncodeUnicodeControlChars() {
        Assert.assertEquals("%C2%80", IRIEncoder.encode("\u0080"));
        Assert.assertEquals("%C2%9F", IRIEncoder.encode("\u009F"));
    }
}
