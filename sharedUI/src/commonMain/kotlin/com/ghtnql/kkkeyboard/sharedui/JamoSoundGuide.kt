package com.ghtnql.kkkeyboard.sharedui

data class JamoSoundGuide(
    val kana: String,
    val hangul: String,
    val noteKo: String,
    val noteJa: String,
    val noteEn: String,
)

data class JamoKanaReading(
    val hiragana: String,
    val katakana: String,
)

private fun composeKanaRow(
    a: String, ya: String, eo: String, yeo: String, o: String,
    yo: String, u: String, yu: String, eu: String, i: String,
): Map<Int, String> = mapOf(
    0 to a, 2 to ya, 4 to eo, 6 to yeo, 8 to o,
    12 to yo, 13 to u, 17 to yu, 18 to eu, 20 to i,
)

private val composeKanaRows: Map<Int, Map<Int, String>> = mapOf(
    // Plain Korean stops are shown with the word-initial approximation used by the learning cards.
    0 to composeKanaRow("か", "きゃ", "こ", "きょ", "こ", "きょ", "く", "きゅ", "く", "き"),
    2 to composeKanaRow("な", "にゃ", "の", "にょ", "の", "にょ", "ぬ", "にゅ", "ぬ", "に"),
    3 to composeKanaRow("た", "てぃゃ", "と", "てぃょ", "と", "てぃょ", "とぅ", "てぃゅ", "とぅ", "てぃ"),
    5 to composeKanaRow("ら", "りゃ", "ろ", "りょ", "ろ", "りょ", "る", "りゅ", "る", "り"),
    6 to composeKanaRow("ま", "みゃ", "も", "みょ", "も", "みょ", "む", "みゅ", "む", "み"),
    7 to composeKanaRow("ぱ", "ぴゃ", "ぽ", "ぴょ", "ぽ", "ぴょ", "ぷ", "ぴゅ", "ぷ", "ぴ"),
    9 to composeKanaRow("さ", "しゃ", "そ", "しょ", "そ", "しょ", "す", "しゅ", "す", "し"),
    11 to composeKanaRow("あ", "や", "お", "よ", "お", "よ", "う", "ゆ", "う", "い"),
    12 to composeKanaRow("ちゃ", "ちゃ", "ちょ", "ちょ", "ちょ", "ちょ", "ちゅ", "ちゅ", "ちゅ", "ち"),
    14 to composeKanaRow("ちゃ", "ちゃ", "ちょ", "ちょ", "ちょ", "ちょ", "ちゅ", "ちゅ", "ちゅ", "ち"),
    15 to composeKanaRow("か", "きゃ", "こ", "きょ", "こ", "きょ", "く", "きゅ", "く", "き"),
    16 to composeKanaRow("た", "てぃゃ", "と", "てぃょ", "と", "てぃょ", "とぅ", "てぃゅ", "とぅ", "てぃ"),
    17 to composeKanaRow("ぱ", "ぴゃ", "ぽ", "ぴょ", "ぽ", "ぴょ", "ぷ", "ぴゅ", "ぷ", "ぴ"),
    18 to composeKanaRow("は", "ひゃ", "ほ", "ひょ", "ほ", "ひょ", "ふ", "ひゅ", "ふ", "ひ"),
)

fun buildJamoKanaReading(consonant: JamoLetter, vowel: JamoLetter): JamoKanaReading {
    val initial = requireNotNull(consonant.initialIndex) { "consonant.initialIndex must be non-null" }
    val medial = requireNotNull(vowel.medialIndex) { "vowel.medialIndex must be non-null" }
    val hiragana = requireNotNull(composeKanaRows[initial]?.get(medial)) {
        "No kana guide for initial=$initial medial=$medial"
    }
    val katakana = buildString(hiragana.length) {
        hiragana.forEach { character ->
            append(if (character in '\u3041'..'\u3096') (character.code + 0x60).toChar() else character)
        }
    }
    return JamoKanaReading(hiragana = hiragana, katakana = katakana)
}

fun jamoSoundGuide(letter: JamoLetter): JamoSoundGuide = when (letter.id) {
    "c_ㄱ" -> JamoSoundGuide(
        kana = "か き く け こ / が ぎ ぐ げ ご",
        hangul = "가 기 구 게 고",
        noteKo = "어두에서는 k에 가깝고 모음 사이에서는 g에 가까워요. ㅋ(카행 거센소리)와 구별해요. 완전히 같은 소리는 아니에요.",
        noteJa = "語頭はか行に近く、母音間ではが行に近づきます。ㅋ（強い息のカ行）と区別します。完全一致ではありません。",
        noteEn = "Like k initially, closer to g between vowels. Contrast aspirated ㅋ. Approximate only.",
    )
    "c_ㄴ" -> JamoSoundGuide(
        kana = "な に ぬ ね の / 終わりは ん (n)",
        hangul = "나 니 누 네 노",
        noteKo = "나행 첫소리, 받침 ㄴ은 ん(n)에 가까워요. 완전히 같은 소리는 아니에요.",
        noteJa = "な行の初声。終声のㄴは ん (n) に近い目安です。完全一致ではありません。",
        noteEn = "Initial n. Final ㄴ ≈ ん (n). Approximate only.",
    )
    "c_ㄷ" -> JamoSoundGuide(
        kana = "た ち つ て と / だ ぢ づ で ど",
        hangul = "다 디 두 데 도",
        noteKo = "어두에서는 t에 가깝고 모음 사이에서는 d에 가까워요. ㅌ(거센소리)와 구별해요. 일본어 ち・つ는 디/두와 달라요.",
        noteJa = "語頭はた行に近く、母音間ではだ行に近づきます。ㅌ（強い息のタ行）と区別します。ち・つはディ/ドゥと違います。",
        noteEn = "Like t initially, closer to d between vowels. Contrast aspirated ㅌ. Japanese ち・つ differ from 디/두. Approximate only.",
    )
    "c_ㄹ" -> JamoSoundGuide(
        kana = "ら り る れ ろ",
        hangul = "라 리 루 레 로",
        noteKo = "모음 앞에서는 ら행처럼 혀끝을 한 번 튕겨요. 받침은 영어 l에 가까워요. 일본어 ら행은 안내용이에요.",
        noteJa = "母音の前ではら行のように舌先をはじく1回の音です。終声は英語のlに近いです。ら行は目安です。",
        noteEn = "Before a vowel, a single tongue tap like the ら-row. Final is l-like. ら-row is a guide only.",
    )
    "c_ㅁ" -> JamoSoundGuide(
        kana = "ま み む め も / 終わりは ん (m)",
        hangul = "마 미 무 메 모",
        noteKo = "마행 첫소리, 받침 ㅁ은 입을 닫는 ん(m)에 가까워요.",
        noteJa = "ま行の初声。終声のㅁは口を閉じる ん (m) に近い目安です。",
        noteEn = "Initial m. Final ㅁ ≈ ん (m, lips closed). Approximate only.",
    )
    "c_ㅂ" -> JamoSoundGuide(
        kana = "ぱ ぴ ぷ ぺ ぽ / ば び ぶ べ ぼ",
        hangul = "바 비 부 베 보",
        noteKo = "어두에서는 p에 가깝고 모음 사이에서는 b에 가까워요. ㅍ(거센소리)와 구별해요.",
        noteJa = "語頭はぱ行に近く、母音間ではば行に近づきます。ㅍ（強い息のパ行）と区別します。",
        noteEn = "Like p initially, closer to b between vowels. Contrast aspirated ㅍ. Approximate only.",
    )
    "c_ㅅ" -> JamoSoundGuide(
        kana = "さ し す せ そ",
        hangul = "사 시 수 세 소",
        noteKo = "시 앞에서는 영어 she에 가까워요. 일본어 さ행은 안내용이에요.",
        noteJa = "ㅣの前では「し」に近い音です。さ行は目安です。",
        noteEn = "Before ㅣ it is close to English she. さ-row is a guide only.",
    )
    "c_ㅇ" -> JamoSoundGuide(
        kana = "あ い う え お / 終わりは ん (ng)",
        hangul = "아 이 우 에 오",
        noteKo = "첫소리 ㅇ은 소리가 없어요. 받침 ㅇ은 코로 나는 ん(ng)에 가까워요.",
        noteJa = "初声のㅇは無音です。終声のㅇは鼻にかかる ん (ng) に近い目安です。",
        noteEn = "Initial ㅇ is silent. Final ㅇ ≈ ん (ng, nasal). Approximate only.",
    )
    "c_ㅈ" -> JamoSoundGuide(
        kana = "ちゃ ち ちゅ ちぇ ちょ / じゃ じ じゅ じぇ じょ",
        hangul = "자 지 주 제 조",
        noteKo = "어두에서는 ch에 가깝고 모음 사이에서는 j에 가까워요. ㅊ(거센소리)와 구별해요.",
        noteJa = "語頭はちゃ行に近く、母音間ではじゃ行に近づきます。ㅊ（強い息のチャ行）と区別します。",
        noteEn = "Like ch initially, closer to j between vowels. Contrast aspirated ㅊ. Approximate only.",
    )
    "c_ㅊ" -> JamoSoundGuide(
        kana = "ちゃ ち ちゅ ちぇ ちょ（強い息）",
        hangul = "차 치 추 체 초",
        noteKo = "ㅈ보다 숨이 강해요. 거센소리예요.",
        noteJa = "ㅈより息が強いです。強い息のチャ行が目安です。",
        noteEn = "Strongly aspirated ch. Contrast unaspirated ㅈ. Approximate only.",
    )
    "c_ㅋ" -> JamoSoundGuide(
        kana = "か き く け こ（強い息）",
        hangul = "카 키 쿠 케 코",
        noteKo = "ㄱ보다 숨이 강해요. 거센소리예요.",
        noteJa = "ㄱより息が強いです。強い息のカ行が目安です。",
        noteEn = "Strongly aspirated k. Contrast plain ㄱ. Approximate only.",
    )
    "c_ㅌ" -> JamoSoundGuide(
        kana = "た ち つ て と（強い息）",
        hangul = "타 티 투 테 토",
        noteKo = "ㄷ보다 숨이 강해요. 거센소리예요.",
        noteJa = "ㄷより息が強いです。強い息のタ行が目安です。",
        noteEn = "Strongly aspirated t. Contrast plain ㄷ. Approximate only.",
    )
    "c_ㅍ" -> JamoSoundGuide(
        kana = "ぱ ぴ ぷ ぺ ぽ（強い息）",
        hangul = "파 피 푸 페 포",
        noteKo = "ㅂ보다 숨이 강해요. 거센소리예요.",
        noteJa = "ㅂより息が強いです。強い息のパ行が目安です。",
        noteEn = "Strongly aspirated p. Contrast plain ㅂ. Approximate only.",
    )
    "c_ㅎ" -> JamoSoundGuide(
        kana = "は ひ ふ へ ほ",
        hangul = "하 히 후 헤 호",
        noteKo = "숨소리예요. 일본어 は행은 안내용이에요.",
        noteJa = "息の音です。は行は目安です。",
        noteEn = "Breathy h. は-row is a guide only.",
    )
    "v_ㅏ" -> JamoSoundGuide("あ", "아", "아에 가까워요.", "あに近い目安です。", "Close to あ. Approximate only.")
    "v_ㅑ" -> JamoSoundGuide("や", "야", "야에 가까워요.", "やに近い目安です。", "Close to や. Approximate only.")
    "v_ㅓ" -> JamoSoundGuide("お / あ の間", "어", "어에 가까워요. 일본어에 같은 소리는 없어요.", "おとあの中間が目安です。同じ音はありません。", "Between o and a. No exact Japanese match.")
    "v_ㅕ" -> JamoSoundGuide("よ / や の間", "여", "여에 가까워요. 일본어에 같은 소리는 없어요.", "よとやの中間が目安です。同じ音はありません。", "Between yo and ya. No exact Japanese match.")
    "v_ㅗ" -> JamoSoundGuide("お", "오", "오에 가까워요. 입을 둥글게 해요.", "おに近い目安です。口を丸めます。", "Close to お with rounded lips. Approximate only.")
    "v_ㅛ" -> JamoSoundGuide("よ", "요", "요에 가까워요.", "よに近い目安です。", "Close to よ. Approximate only.")
    "v_ㅜ" -> JamoSoundGuide("う", "우", "우에 가까워요. 입술을 내밀어요.", "うに近い目安です。唇を突き出します。", "Close to う with protruded lips. Approximate only.")
    "v_ㅠ" -> JamoSoundGuide("ゆ", "유", "유에 가까워요.", "ゆに近い目安です。", "Close to ゆ. Approximate only.")
    "v_ㅡ" -> JamoSoundGuide("う（唇を丸めない）", "으", "입술을 둥글게 하지 않고 으 소리를 내요. 우와 구별해요.", "口を丸めないうが目安です。우と区別します。", "う without lip rounding. Contrast 우. Approximate only.")
    "v_ㅣ" -> JamoSoundGuide("い", "이", "이에 가까워요.", "いに近い目安です。", "Close to い. Approximate only.")
    else -> JamoSoundGuide("あ", letter.glyph, "안내용이에요.", "目安です。", "Guide only.")
}

data class HiraganaGuideRow(
    val kana: List<String>,
    val hangul: List<String>,
)

// Kana rows checked against Japan Foundation Irodori Kana_all.pdf:
// https://www.irodori.jpf.go.jp/assets/data/Kana_all.pdf
// Hangul readings are approximate; they are separate from Korean jamo guides.
val hiraganaGuideRows: List<HiraganaGuideRow> = listOf(
    HiraganaGuideRow(listOf("あ", "い", "う", "え", "お"), listOf("아", "이", "우", "에", "오")),
    HiraganaGuideRow(listOf("か", "き", "く", "け", "こ"), listOf("카", "키", "쿠", "케", "코")),
    HiraganaGuideRow(listOf("さ", "し", "す", "せ", "そ"), listOf("사", "시", "스", "세", "소")),
    HiraganaGuideRow(listOf("た", "ち", "つ", "て", "と"), listOf("타", "치", "쓰", "테", "토")),
    HiraganaGuideRow(listOf("な", "に", "ぬ", "ね", "の"), listOf("나", "니", "누", "네", "노")),
    HiraganaGuideRow(listOf("は", "ひ", "ふ", "へ", "ほ"), listOf("하", "히", "후", "헤", "호")),
    HiraganaGuideRow(listOf("ま", "み", "む", "め", "も"), listOf("마", "미", "무", "메", "모")),
    HiraganaGuideRow(listOf("や", "", "ゆ", "", "よ"), listOf("야", "", "유", "", "요")),
    HiraganaGuideRow(listOf("ら", "り", "る", "れ", "ろ"), listOf("라", "리", "루", "레", "로")),
    HiraganaGuideRow(listOf("わ", "", "", "", "を"), listOf("와", "", "", "", "오")),
    HiraganaGuideRow(listOf("ん", "", "", "", ""), listOf("ㄴ·ㅁ·ㅇ", "", "", "", "")),
    HiraganaGuideRow(listOf("が", "ぎ", "ぐ", "げ", "ご"), listOf("가", "기", "구", "게", "고")),
    HiraganaGuideRow(listOf("ざ", "じ", "ず", "ぜ", "ぞ"), listOf("자", "지", "즈", "제", "조")),
    HiraganaGuideRow(listOf("だ", "ぢ", "づ", "で", "ど"), listOf("다", "지", "즈", "데", "도")),
    HiraganaGuideRow(listOf("ば", "び", "ぶ", "べ", "ぼ"), listOf("바", "비", "부", "베", "보")),
    HiraganaGuideRow(listOf("ぱ", "ぴ", "ぷ", "ぺ", "ぽ"), listOf("파", "피", "푸", "페", "포")),
)
