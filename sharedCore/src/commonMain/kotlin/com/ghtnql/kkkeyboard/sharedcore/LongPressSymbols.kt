package com.ghtnql.kkkeyboard.sharedcore

/** Editable slots exclude the fixed tense consonant, which always comes first. */
data class LongPressKey(val id: String, val label: String, val defaultSlots: List<String>, val fixedJamo: String = "")

object LongPressCatalog {
    private fun entry(id: String, first: String = "", second: String = "", fixed: String = "", label: String = id) =
        LongPressKey(id, label, listOf(first, second, ""), fixed)

    private val cheonjiin = listOf(
        entry("ㅣ", "1", "!"), entry("·", "2", "@"), entry("ㅡ", "3", "#"),
        entry("ㄱㅋ", "4", "$"), entry("ㄴㄹ", "5", "%"), entry("ㄷㅌ", "6", "^"),
        entry("ㅂㅍ", "7", "&"), entry("ㅅㅎ", "8", "*"), entry("ㅈㅊ", "9", "("),
        entry("ㅇㅁ", "0", ")"),
    )
    private val plus = listOf(
        entry("ㅣ", "1", "!"), entry("·", "2", "@"), entry("ㅡ", "3", "#"),
        entry("ㄱ", "4", "$"), entry("ㅋ", ",", fixed = "ㄲ"), entry("ㄴ", "5", "%"), entry("ㄹ", "."),
        entry("ㄷ", "6", "^"), entry("ㅌ", "?", fixed = "ㄸ"),
        entry("ㅂ", "7", "&"), entry("ㅍ", "/", fixed = "ㅃ"), entry("ㅅ", "8", "*"), entry("ㅎ", ";", fixed = "ㅆ"),
        entry("ㅈ", "9", "("), entry("ㅊ", ":", fixed = "ㅉ"), entry("ㅇ", "0", ")"), entry("ㅁ", "-"),
    )
    private val qwerty: List<LongPressKey> = run {
        val ids = "qwertyuiopasdfghjklzxcvbnm"
        val labels = "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ"
        val numbers = "1234567890"
        val shifted = "!@#$%^&*()"
        val punctuation = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "*", "\"", "'", ":", ";", "!", "?")
        ids.mapIndexed { index, id ->
            if (index < 10) entry(id.toString(), numbers[index].toString(), shifted[index].toString(), label = labels[index].toString())
            else entry(id.toString(), punctuation[index - 10], label = labels[index].toString())
        }
    }

    fun keys(layoutId: String): List<LongPressKey> = when (layoutId) {
        "cheonjiin" -> cheonjiin
        "cheonjiin_plus" -> plus
        "qwerty" -> qwerty
        else -> emptyList()
    }
    fun key(layoutId: String, keyId: String): LongPressKey? = keys(layoutId).firstOrNull { it.id == keyId }
    fun slots(layoutId: String, keyId: String, override: List<String>?): List<String> {
        val values = override ?: key(layoutId, keyId)?.defaultSlots.orEmpty()
        return List(3) { values.getOrElse(it) { "" } }
    }
    fun choices(layoutId: String, keyId: String, slots: List<String>): List<String> {
        val key = key(layoutId, keyId) ?: return emptyList()
        return listOfNotNull(key.fixedJamo.takeIf { it.isNotEmpty() }) +
            slots(layoutId, keyId, slots).filter { it.isNotEmpty() }
    }

    fun storageKey(layoutId: String, keyId: String): String = "longpress_${layoutId}_${keyId}"
}

/** Each pointer sequence can emit one tap or one hold; cancellation emits neither. */
class LongPressGestureState {
    private var active = false
    private var held = false
    fun down() { active = true; held = false }
    fun hold(): Boolean {
        if (!active || held) return false
        held = true
        return true
    }
    fun release(): Boolean {
        val tap = active && !held
        active = false
        return tap
    }
    fun cancel() { active = false; held = false }
}
