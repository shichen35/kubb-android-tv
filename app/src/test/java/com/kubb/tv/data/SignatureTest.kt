package com.kubb.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Vectors captured from the site in a browser. */
class SignatureTest {
    // pConfig mode (the homepage's inlined keys): the private key derives from the public key.
    private fun derivePriv(pub: String) = pub.substring(2, 6) + pub.substring(1, 17)
    private fun tsPriv(pub: String) = listOf(
        "version001", "vers1on001", "vers1on00i", "bersion001", "vcrsion001", "versi0n001", "versio_001", "version0o1",
    )[(pub.toLong() % 8).toInt()]

    private val p1 = "CJSvCp8qE34qDounEJbVLLDVDZSkCJSqBZ8qE2uoC5yO6fcOcXkR6HePcR8Q61iRCBAmi9kRCPmoCnmRCJCmiIzDJGqPJDcE3OoOZTcDZavD38sOJ8oPMCpEMKoEJLaOJ1"
    private val p2 = "CJSvCp8qE34uDYuqCZXVLLDVDZSkCJSqBZ8qE2uoC5yPiJARcHiPd9cPd1YQCB6QCJ8o6nYPcHgocHmnCnaQiAzOMPXP6CrCZKpCMCsEMKnC3bZP64pDcOnE34vCMHXCZ3"
    private val p3 = "CJSvCp8qE3GuEIunE3DVLLDVDZSkCJSqBZ8qE2uoC5yp6Z2ScpAmi9cR69mQCfcS71oQCZ4p69mO71gP79omcozDparDsOoOZCrEM8vDsKpE6KpPM4qDcGoCpKoOpOvP32"

    private val vectors = listOf(
        Triple("isPromotion=4&region=US&v=1", "1793248121136", "9e2be7b65275af441ad7631abc173728"),
        Triple("isPromotion=4&region=US&v=1", "1793248147246", "a38de8e058f39e4b2303ecf8b88818d8"),
        Triple("version=1&v=1", p1, "b65b83fc9b82490575a5c90996c590c1"),
        Triple("cinema=1&cid=0,1,3&v=1", p1, "7f1e3a1818bc050673277eac87b6931f"),
        Triple("cinema=1&page=1&size=36&orderby=0&desc=1&cid=0,1,3&isserial=-1&isIndex=-1&isfree=-1&isVertical=-1&v=1", p1, "904fa11b6834381581e168f274ceaebf"),
        Triple("cinema=1&id=uHcU4Clu390&a=1&lang=none&usersign=1&region=US&device=1&isMasterSupport=1&v=1", p2, "db06a24b0f00b0aa5bad55efb537bd79"),
        Triple("cinema=1&device=1&player=CkPlayer&tech=HLS&country=HU&lang=cns&v=1&id=uHcU4Clu390&region=US", p2, "54bd1f322385790f4d29a431c56185e2"),
        Triple("cinema=1&vid=uHcU4Clu390&lsk=1&taxis=0&cid=0,1,4,132&v=1", p2, "add83511bee3d6860c6559a5d0d21452"),
        Triple("cinema=1&cid=0,1,4,132&title=%E7%BE%8E%E4%BA%BA%E4%BD%99&size=10&set=1&id=50365&v=1", p2, "466629f095734e63403bb5c4c6b0ed5c"),
        Triple("cinema=1&page=1&size=36&orderby=3&desc=1&cid=0,1,4&region=%E5%A4%A7%E9%99%86&isserial=-1&isIndex=-1&isfree=-1&isVertical=-1&v=1", p3, "379724af6acbea328a4161f3e81b908a"),
    )

    @Test
    fun browserVectors() {
        for ((qs, pub, want) in vectors) {
            val priv = if (pub.all(Char::isDigit)) tsPriv(pub) else derivePriv(pub)
            assertEquals(qs, want, Signature.sign(qs, pub, priv))
        }
    }

    @Test
    fun md5MatchesReferenceDigests() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Signature.md5(""))
        assertEquals("6c2be72683d479a0103075f64e561cf0", Signature.md5("大陆"))
    }

    @Test
    fun canonicalQueryDecodesValuesAndPlus() {
        assertEquals("a=大陆&b=x y&c", Signature.canonicalQuery("a=%E5%A4%A7%E9%99%86&b=x+y&c"))
    }

    @Test
    fun signParamsUsesTimestampKeyTable() {
        assertEquals(
            "vv=9e2be7b65275af441ad7631abc173728&pub=1793248121136",
            Signature.signParams("isPromotion=4&region=US&v=1", 1793248121136),
        )
    }

    @Test
    fun buildQueryEncodesLikeTheSite() {
        assertEquals(
            "cid=0,1,4&region=%E5%A4%A7%E9%99%86&t=a%20b",
            Gateway.buildQuery(listOf("cid" to "0,1,4", "skip" to null, "region" to "大陆", "t" to "a b")),
        )
    }
}
