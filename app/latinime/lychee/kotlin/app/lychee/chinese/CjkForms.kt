package app.lychee.chinese

/**
 * Characters made outside China, which only one language writes: Japan's (kokuji) and Korea's
 * (gukja). The word cards name them, and handwriting offers each only on its own keyboard.
 */
object CjkForms {
    /**
     * Characters made in Japan (kokuji): Wiktionary's "Japanese-coined CJKV characters"
     * (every one it lists, the rare ones in Unicode's extensions too), and the common ones
     * it leaves out.
     */
    val KOKUJI: Set<String> = chars("Japanese-coined CJKV characters")

    /**
     * Characters made in Korea (gukja): Wiktionary's "Korean-only CJKV characters" (all 99),
     * with 乫 and 哛 from Wikipedia's lists (乭 畓 欌 頉 among them).
     */
    val GUKJA: Set<String> = chars("㐃乤乥乬乯乲乺乻㐇㐈㐉㐊㐋𠃗㐎㐏乧㐓乭㐘乶乷㐠乼乽㐢亇亪亽仒侤㒱㔖㔔厁唟㖯㖰㖱㖲㖳喸嗭㕾㖙㖝唜㗟㗠㗡壭夈夞夻岾巪巬巭巼廤㣴怾敾㪲㪳旀㫇㫈旕栍椧榌橴欌櫷欕㭐㭗歚㳓𣴘㳲㵜㶂㶃㸴獤畓䃼䆆筽莻虄迲䣉闏頔頉䭏乫哛")

    /** Each character of [s], by code point (some are beyond the Basic Multilingual Plane). */
    private fun chars(s: String): Set<String> = s.codePoints().toArray().map { String(Character.toChars(it)) }.toHashSet()
}
