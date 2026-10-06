package com.ghtnql.kkkeyboard.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghtnql.kkkeyboard.sharedcore.HangulProbe

data class JamoLetter(
    val id: String,
    val glyph: String,
    val name: String,
    val example: String,
    val initialIndex: Int? = null,
    val medialIndex: Int? = null,
)

val basicConsonants: List<JamoLetter> = listOf(
    JamoLetter("c_ㄱ", "ㄱ", "기역", "가", 0, null),
    JamoLetter("c_ㄴ", "ㄴ", "니은", "나", 2, null),
    JamoLetter("c_ㄷ", "ㄷ", "디귿", "다", 3, null),
    JamoLetter("c_ㄹ", "ㄹ", "리을", "라", 5, null),
    JamoLetter("c_ㅁ", "ㅁ", "미음", "마", 6, null),
    JamoLetter("c_ㅂ", "ㅂ", "비읍", "바", 7, null),
    JamoLetter("c_ㅅ", "ㅅ", "시옷", "사", 9, null),
    JamoLetter("c_ㅇ", "ㅇ", "이응", "아", 11, null),
    JamoLetter("c_ㅈ", "ㅈ", "지읒", "자", 12, null),
    JamoLetter("c_ㅊ", "ㅊ", "치읓", "차", 14, null),
    JamoLetter("c_ㅋ", "ㅋ", "키읔", "카", 15, null),
    JamoLetter("c_ㅌ", "ㅌ", "티읕", "타", 16, null),
    JamoLetter("c_ㅍ", "ㅍ", "피읖", "파", 17, null),
    JamoLetter("c_ㅎ", "ㅎ", "히읗", "하", 18, null),
)

val basicVowels: List<JamoLetter> = listOf(
    JamoLetter("v_ㅏ", "ㅏ", "아", "아", null, 0),
    JamoLetter("v_ㅑ", "ㅑ", "야", "야", null, 2),
    JamoLetter("v_ㅓ", "ㅓ", "어", "어", null, 4),
    JamoLetter("v_ㅕ", "ㅕ", "여", "여", null, 6),
    JamoLetter("v_ㅗ", "ㅗ", "오", "오", null, 8),
    JamoLetter("v_ㅛ", "ㅛ", "요", "요", null, 12),
    JamoLetter("v_ㅜ", "ㅜ", "우", "우", null, 13),
    JamoLetter("v_ㅠ", "ㅠ", "유", "유", null, 17),
    JamoLetter("v_ㅡ", "ㅡ", "으", "으", null, 18),
    JamoLetter("v_ㅣ", "ㅣ", "이", "이", null, 20),
)

fun buildJamoSyllable(consonant: JamoLetter, vowel: JamoLetter): String {
    require(consonant.initialIndex != null) { "consonant.initialIndex must be non-null" }
    require(vowel.medialIndex != null) { "vowel.medialIndex must be non-null" }
    return HangulProbe().compose(consonant.initialIndex, vowel.medialIndex, 0)
}

private val validJamoIds: Set<String> = (basicConsonants + basicVowels).map { it.id }.toSet()

private class JamoChrome(val lang: UiLanguage) {
    val title = when (lang) { UiLanguage.JA -> "ハングル文字学習"; UiLanguage.EN -> "Hangul letters"; else -> "한글 자모 학습" }
    val close = when (lang) { UiLanguage.JA -> "閉じる"; UiLanguage.EN -> "Close"; else -> "닫기" }
    val tabConsonant = when (lang) { UiLanguage.JA -> "子音"; UiLanguage.EN -> "Consonants"; else -> "자음" }
    val tabVowel = when (lang) { UiLanguage.JA -> "母音"; UiLanguage.EN -> "Vowels"; else -> "모음" }
    val tabCompose = when (lang) { UiLanguage.JA -> "文字づくり"; UiLanguage.EN -> "Combine"; else -> "글자 만들기" }
    val tabWord = when (lang) { UiLanguage.JA -> "単語クイズ"; UiLanguage.EN -> "Word quiz"; else -> "단어 퀴즈" }
    val quizButton = when (lang) { UiLanguage.JA -> "確認問題"; UiLanguage.EN -> "Quiz"; else -> "확인 문제" }
    val backToList = when (lang) { UiLanguage.JA -> "学習リスト"; UiLanguage.EN -> "Back to list"; else -> "학습 목록" }
    val learned = when (lang) { UiLanguage.JA -> "学習済み"; UiLanguage.EN -> "Learned"; else -> "학습 완료" }
    val progress = { done: Int, total: Int -> when (lang) { UiLanguage.JA -> "$total 問中 $done 済"; UiLanguage.EN -> "$done / $total learned"; else -> "${total}개 중 ${done}개 학습" } }
    val quizWrong = when (lang) { UiLanguage.JA -> "もう一度選んでください"; UiLanguage.EN -> "Try again"; else -> "다시 골라 보세요" }
    val quizCorrect = when (lang) { UiLanguage.JA -> "正解！"; UiLanguage.EN -> "Correct!"; else -> "정답!" }
    val nextLetter = when (lang) { UiLanguage.JA -> "次の文字"; UiLanguage.EN -> "Next letter"; else -> "다음 글자" }
    val nextQuestion = when (lang) { UiLanguage.JA -> "次の問題"; UiLanguage.EN -> "Next question"; else -> "다음 문제" }
    val replay = when (lang) { UiLanguage.JA -> "もう一度"; UiLanguage.EN -> "Replay"; else -> "다시 풀기" }
    val exampleLabel = when (lang) { UiLanguage.JA -> "例"; UiLanguage.EN -> "Example"; else -> "예시" }
    val pickConsonant = when (lang) { UiLanguage.JA -> "子音を選ぶ"; UiLanguage.EN -> "Pick a consonant"; else -> "자음 고르기" }
    val pickVowel = when (lang) { UiLanguage.JA -> "母音を選ぶ"; UiLanguage.EN -> "Pick a vowel"; else -> "모음 고르기" }
    val silentNote = when (lang) { UiLanguage.JA -> "初声のㅇは音がありません。終声は使いません。"; UiLanguage.EN -> "Initial ㅇ is silent. No final consonant here."; else -> "첫소리 ㅇ은 소리가 나지 않아요. 받침은 사용하지 않아요." }
    val pronunciationGuide = when (lang) { UiLanguage.JA -> "発音の目安"; UiLanguage.EN -> "Pronunciation guide"; else -> "발음 안내" }
    val approxNote = when (lang) { UiLanguage.JA -> "カナは発音の目安です。同じ音とは限りません"; UiLanguage.EN -> "Kana is an approximate sound guide, not an exact match"; else -> "일본어 표기는 발음 안내용이며 완전히 같은 소리는 아니에요" }
    val noExample = when (lang) { UiLanguage.JA -> "例がありません"; UiLanguage.EN -> "No examples available"; else -> "예시가 없어요" }
    val noQuestion = when (lang) { UiLanguage.JA -> "問題がありません"; UiLanguage.EN -> "No questions available"; else -> "문제가 없어요" }
    val koreanWordLabel = when (lang) { UiLanguage.JA -> "韓国語の単語"; UiLanguage.EN -> "Korean word"; else -> "한국어 단어" }
    val taskPronunciation = when (lang) { UiLanguage.JA -> "ハングル読みを選んでください"; UiLanguage.EN -> "Choose the Hangul pronunciation"; else -> "한글 발음을 고르세요" }
    val taskKoreanWord = when (lang) { UiLanguage.JA -> "韓国語の単語：日本語→韓国語の答えを選んでください"; UiLanguage.EN -> "Korean word: choose the Korean answer for this Japanese prompt"; else -> "한국어 단어: 일본어에 대한 한국어 답을 고르세요" }
    val guideTitle = when (lang) { UiLanguage.JA -> "ひらがな発音表"; UiLanguage.EN -> "Hiragana sound chart"; else -> "히라가나 발음표" }
    val guideExplain = when (lang) { UiLanguage.JA -> "ひらがなは発音の目安です。同じ音とは限りません。終声ㄴ・ㅁ・ㅇは ん (n/m/ng) が目安です。"; UiLanguage.EN -> "Hiragana is a sound guide only, not exact. Final ㄴ/ㅁ/ㅇ ≈ ん (n/m/ng)."; else -> "히라가나는 발음 안내용이며 같은 소리가 아니에요. 받침 ㄴ/ㅁ/ㅇ은 ん (n/m/ng)이 안내예요." }
    val helpDesc = when (lang) { UiLanguage.JA -> "ひらがな発音表を開く"; UiLanguage.EN -> "Open hiragana sound chart"; else -> "히라가나 발음표 열기" }
}

private fun taskTitle(kind: JamoWordKind, chrome: JamoChrome): String =
    if (kind == JamoWordKind.JAPANESE_PRONUNCIATION) chrome.taskPronunciation else chrome.taskKoreanWord

private fun guideNote(guide: JamoSoundGuide, lang: UiLanguage): String =
    when (lang) { UiLanguage.JA -> guide.noteJa; UiLanguage.EN -> guide.noteEn; else -> guide.noteKo }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JamoLearningScreen(
    platform: AppPlatform,
    onClose: () -> Unit,
    onEffect: (GameSoundEffect) -> Unit = {},
) {
    val s = LocalUiStrings.current
    val t = remember(s.language) { JamoChrome(s.language) }
    val catalog = remember(platform.learningItems) { JamoWordCatalog(platform.learningItems) }
    var tab by remember { mutableIntStateOf(0) }
    var learnedIds by remember {
        mutableStateOf(platform.readHangulLearnedIds().filter { it in validJamoIds }.toSet())
    }
    var selected: JamoLetter? by remember { mutableStateOf(null) }
    var consonantPick by remember { mutableStateOf(basicConsonants[0]) }
    var vowelPick by remember { mutableStateOf(basicVowels[0]) }
    var showGuide by remember { mutableStateOf(!platform.readHiraganaGuideSeen()) }
    fun dismissGuide() {
        platform.saveHiraganaGuideSeen()
        showGuide = false
    }

    fun markLearned(id: String) {
        if (id in learnedIds) return
        val next = (learnedIds + id).filter { it in validJamoIds }
        learnedIds = next.toSet()
        platform.saveHangulLearnedIds(next)
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(t.title, fontSize = 20.sp, maxLines = 2, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { onEffect(GameSoundEffect.HIT); showGuide = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("hiragana-help").semantics { contentDescription = t.helpDesc },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) { Text("?", fontSize = 20.sp) }
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(t.close) }
        }
        LinearProgressIndicator(progress = { learnedIds.size / 24f }, modifier = Modifier.fillMaxWidth())
        Text(t.progress(learnedIds.size, 24))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { onEffect(GameSoundEffect.HIT); tab = 0; selected = null }, label = { Text(t.tabConsonant) }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(selected = tab == 1, onClick = { onEffect(GameSoundEffect.HIT); tab = 1; selected = null }, label = { Text(t.tabVowel) }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(selected = tab == 2, onClick = { onEffect(GameSoundEffect.HIT); tab = 2; selected = null }, label = { Text(t.tabCompose) }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(selected = tab == 3, onClick = { onEffect(GameSoundEffect.HIT); tab = 3; selected = null }, label = { Text(t.tabWord) }, modifier = Modifier.heightIn(min = 48.dp))
        }
        when {
            selected != null -> JamoDetail(
                letter = selected!!, learned = selected!!.id in learnedIds,
                all = if (tab == 1) basicVowels else basicConsonants,
                catalog = catalog,
                onLearned = { markLearned(it) },
                onNext = { onEffect(GameSoundEffect.HIT); selected = it },
                onBack = { onEffect(GameSoundEffect.HIT); selected = null },
                onClose = onClose,
                onEffect = onEffect,
            )
            tab == 2 -> ComposeTab(
                consonant = consonantPick, vowel = vowelPick,
                onConsonant = { onEffect(GameSoundEffect.HIT); consonantPick = it },
                onVowel = { onEffect(GameSoundEffect.HIT); vowelPick = it },
                chrome = t,
            )
            tab == 3 -> WordQuizTab(catalog = catalog, chrome = t, onEffect = onEffect)
            else -> JamoGrid(
                letters = if (tab == 0) basicConsonants else basicVowels,
                learnedIds = learnedIds,
                onPick = { onEffect(GameSoundEffect.HIT); selected = it },
            )
        }
    }
    if (showGuide) {
        HiraganaGuideDialog(chrome = t, onDismiss = {
            onEffect(GameSoundEffect.HIT)
            dismissGuide()
        })
    }
}

@Composable
private fun HiraganaGuideDialog(chrome: JamoChrome, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("hiragana-guide"),
        onDismissRequest = onDismiss,
        title = { Text(chrome.guideTitle, fontSize = 20.sp) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(chrome.guideExplain, fontSize = 14.sp)
                Text(chrome.approxNote, fontSize = 14.sp)
                hiraganaGuideRows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.kana.forEachIndexed { i, k ->
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(k, fontSize = 22.sp, textAlign = TextAlign.Center)
                                Text(row.hangul.getOrElse(i) { "" }, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                        repeat((5 - row.kana.size).coerceAtLeast(0)) {
                            Row(Modifier.weight(1f)) { }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).testTag("hiragana-guide-close"),
            ) { Text(chrome.close) }
        },
    )
}

@Composable
private fun JamoGrid(letters: List<JamoLetter>, learnedIds: Set<String>, onPick: (JamoLetter) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        letters.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { l ->
                    val mark = if (l.id in learnedIds) " ✓" else ""
                    Button(
                        onClick = { onPick(l) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) { Text(l.glyph + mark, fontSize = 20.sp, maxLines = 1) }
                }
                repeat(4 - row.size) { Row(Modifier.weight(1f)) { } }
            }
        }
    }
}

@Composable
private fun JamoDetail(
    letter: JamoLetter, learned: Boolean, all: List<JamoLetter>,
    catalog: JamoWordCatalog,
    onLearned: (String) -> Unit,
    onNext: (JamoLetter) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onEffect: (GameSoundEffect) -> Unit,
) {
    val s = LocalUiStrings.current
    val t = remember(s.language) { JamoChrome(s.language) }
    var quiz by remember(letter.id) { mutableStateOf(false) }
    var wrong by remember(letter.id) { mutableStateOf(false) }
    var done by remember(letter.id) { mutableStateOf(false) }
    val examples = remember(catalog, letter.id) { catalog.examples(letter) }
    val exampleIds = remember(examples) { examples.map { it.sourceId }.toSet() }
    var question by remember(letter.id) { mutableStateOf(catalog.question(letter, excludedSourceIds = exampleIds)) }
    val guide = remember(letter.id) { jamoSoundGuide(letter) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!quiz) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(letter.glyph, fontSize = 56.sp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (letter.initialIndex != null) guide.kana.replace(" / ", "\n") else guide.kana, fontSize = 22.sp, softWrap = true, modifier = Modifier.testTag("jamo-reading"))
                            Text(guide.hangul, fontSize = 22.sp, softWrap = true, modifier = Modifier.testTag("jamo-sound-hangul"))
                            Text(letter.name, fontSize = 14.sp)
                        }
                    }
                    Text(guideNote(guide, s.language), modifier = Modifier.testTag("jamo-sound-note"))
                    Text(t.approxNote)
                    if (examples.isEmpty()) {
                        Text("${t.exampleLabel}: ${t.noExample}")
                    } else {
                        examples.forEach { w ->
                            if (w.kind == JamoWordKind.JAPANESE_PRONUNCIATION) {
                                Text("${w.japanese} → ${w.hangul} (${w.koreanMeaning})")
                            } else {
                                Text("${t.koreanWordLabel}: ${w.japanese} → ${w.hangul} (${w.koreanMeaning})")
                            }
                        }
                    }
                    if (learned) Text(t.learned)
                }
            }
            Button(onClick = { onEffect(GameSoundEffect.HIT); quiz = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t.quizButton) }
        } else {
            val q = question
            if (q == null) {
                Text(t.noQuestion)
            } else if (!done) {
                Text(taskTitle(q.word.kind, t))
                Text(q.word.japanese, fontSize = 24.sp, modifier = Modifier.testTag("jamo-word-question"))
                q.options.forEach { o ->
                    OutlinedButton(
                        onClick = {
                            if (o.sourceId == q.word.sourceId) {
                                onEffect(GameSoundEffect.HIT)
                                done = true
                                onLearned(letter.id)
                            } else {
                                onEffect(GameSoundEffect.ERROR)
                                wrong = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("jamo-word-choice-" + o.sourceId),
                    ) { Text(o.hangul, fontSize = 22.sp) }
                }
                if (wrong) Text(t.quizWrong)
            } else {
                Text(t.quizCorrect)
                Column(Modifier.testTag("jamo-word-reveal"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(taskTitle(q.word.kind, t))
                    if (q.word.kind == JamoWordKind.JAPANESE_PRONUNCIATION) {
                        Text("${q.word.japanese} → ${q.word.hangul} (${q.word.koreanMeaning})")
                    } else {
                        Text("${t.koreanWordLabel}: ${q.word.japanese} → ${q.word.hangul} (${q.word.koreanMeaning})")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onEffect(GameSoundEffect.HIT)
                            wrong = false; done = false
                            val nextExcluded = exampleIds + q.word.sourceId
                            question = catalog.question(letter, excludedSourceIds = nextExcluded)
                                ?: catalog.question(letter, excludedSourceIds = exampleIds)
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(t.replay) }
                    val next = all.getOrNull(all.indexOf(letter) + 1)
                    if (next != null) {
                        Button(onClick = { onNext(next) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t.nextLetter) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(t.backToList) }
            OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(t.close) }
        }
    }
}

@Composable
private fun WordQuizTab(
    catalog: JamoWordCatalog,
    chrome: JamoChrome,
    onEffect: (GameSoundEffect) -> Unit,
) {
    var wrong by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var question by remember(catalog) { mutableStateOf(catalog.question()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val q = question
        if (q == null) {
            Text(chrome.noQuestion)
        } else if (!done) {
            Text(taskTitle(q.word.kind, chrome))
            Text(q.word.japanese, fontSize = 24.sp, modifier = Modifier.testTag("jamo-word-question"))
            q.options.forEach { o ->
                OutlinedButton(
                    onClick = {
                        if (o.sourceId == q.word.sourceId) {
                            onEffect(GameSoundEffect.HIT)
                            done = true
                        } else {
                            onEffect(GameSoundEffect.ERROR)
                            wrong = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("jamo-word-choice-" + o.sourceId),
                ) { Text(o.hangul, fontSize = 22.sp) }
            }
            if (wrong) Text(chrome.quizWrong)
        } else {
            Text(chrome.quizCorrect)
            Column(Modifier.testTag("jamo-word-reveal"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(taskTitle(q.word.kind, chrome))
                if (q.word.kind == JamoWordKind.JAPANESE_PRONUNCIATION) {
                    Text("${q.word.japanese} → ${q.word.hangul} (${q.word.koreanMeaning})")
                } else {
                    Text("${chrome.koreanWordLabel}: ${q.word.japanese} → ${q.word.hangul} (${q.word.koreanMeaning})")
                }
            }
            Button(
                onClick = {
                    onEffect(GameSoundEffect.HIT)
                    wrong = false; done = false
                    val nextExcluded = setOf(q.word.sourceId)
                    question = catalog.question(excludedSourceIds = nextExcluded) ?: catalog.question()
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(chrome.nextQuestion) }
        }
    }
}

@Composable
private fun ComposeTab(
    consonant: JamoLetter, vowel: JamoLetter,
    onConsonant: (JamoLetter) -> Unit, onVowel: (JamoLetter) -> Unit, chrome: JamoChrome,
) {
    val syllable = buildJamoSyllable(consonant, vowel)
    val reading = buildJamoKanaReading(consonant, vowel)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${consonant.glyph} + ${vowel.glyph} = $syllable", fontSize = 24.sp)
                Text(syllable, fontSize = 56.sp)
                Text(
                    "${chrome.pronunciationGuide}　${reading.katakana}（${reading.hiragana}）",
                    fontSize = 18.sp,
                    modifier = Modifier.testTag("jamo-compose-pronunciation"),
                )
                Text(chrome.silentNote)
            }
        }
        Text(chrome.pickConsonant)
        consonant.chunkRows(basicConsonants, onConsonant)
        Text(chrome.pickVowel)
        vowel.chunkRows(basicVowels, onVowel)
    }
}

@Composable
private fun JamoLetter.chunkRows(all: List<JamoLetter>, onPick: (JamoLetter) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        all.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { l ->
                    val sel = l.id == this@chunkRows.id
                    if (sel) Button(onClick = { onPick(l) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(l.glyph, fontSize = 20.sp) }
                    else OutlinedButton(onClick = { onPick(l) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(l.glyph, fontSize = 20.sp) }
                }
                repeat(4 - row.size) { Row(Modifier.weight(1f)) { } }
            }
        }
    }
}
