package com.ghtnql.kkkeyboard.sharedui

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalUiStrings = staticCompositionLocalOf { UiStrings(UiLanguage.KO) }

/** UI language for static app chrome. Learning content never passes through this. */
public enum class UiLanguage {
    KO, JA, EN;

    public companion object {
        /** Normalizes tags like "ja-JP", "ja_JP", "ja", "en-US", "ko-KR". Unknown -> KO. */
        public fun fromTag(tag: String): UiLanguage {
            val normalized = tag.trim().lowercase().replace('_', '-')
            val base = normalized.substringBefore('-')
            return when (base) {
                "ja" -> JA
                "en" -> EN
                else -> KO
            }
        }
    }
}

/**
 * Explicit ko/ja/en translations for the static UI strings found in KKKeyboardApp.kt.
 *
 * Covers: home tabs, onboarding, settings and buttons, phrases, themes, modes,
 * page titles, result, progress, dialog, audio, game text.
 *
 * [text] maps a known Korean static label to the current language; it is a fixed
 * dictionary lookup, never a regex, and learning content must never be passed
 * through it. Dynamic labels (counts, durations, scores) use the typed
 * functions below.
 */
public class UiStrings(public val language: UiLanguage) {
    public val adRemovalThemeAccess: String = when(language) {
        UiLanguage.KO -> "광고 제거 구매로 이용 가능"
        UiLanguage.JA -> "広告削除の購入で利用可能"
        UiLanguage.EN -> "Included with ad removal"
    }
    public fun seoulAdStatus(message: String): String {
        val words = when(message) {
            "광고 개인정보 선택이 완료되지 않았습니다." -> "広告のプライバシー設定を完了してください。" to "Complete your ad privacy choice first."
            "광고 설정이 준비되지 않았습니다.", "서울 테마 광고가 아직 준비되지 않았습니다." -> "広告はまだ利用できません。" to "Ads are currently unavailable."
            "광고를 불러오는 중입니다." -> "広告を読み込み中です。" to "Loading the ad."
            "공유 저장소를 사용할 수 없어 테마를 잠금 해제할 수 없습니다." -> "共有設定を利用できないため、テーマを解除できません。" to "Shared settings are unavailable; the theme cannot be unlocked."
            "광고를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.", "광고를 불러오지 못했습니다. 다시 시도해 주세요." -> "広告を読み込めませんでした。もう一度お試しください。" to "Could not load the ad. Please try again."
            "광고를 표시하지 못했습니다. 다시 시도해 주세요.", "광고를 표시할 수 있는 화면이 없습니다. 다시 시도해 주세요." -> "広告を表示できませんでした。もう一度お試しください。" to "Could not show the ad. Please try again."
            "광고 보상이 완료되지 않아 테마가 잠겨 있습니다.", "광고 보상을 받지 못했습니다. 다시 시도해 주세요." -> "広告の報酬を受け取れませんでした。もう一度お試しください。" to "The ad reward was not completed. Please try again."
            "서울 테마가 24시간 열렸습니다.", "서울 테마가 24시간 잠금 해제되었습니다." -> "ソウルテーマを24時間利用できます。" to "Seoul themes are unlocked for 24 hours."
            "테마를 저장할 수 없습니다. 다시 시도해 주세요." -> "テーマを保存できませんでした。もう一度お試しください。" to "Could not save the theme. Please try again."
            else -> return message
        }
        return when(language) { UiLanguage.KO -> message; UiLanguage.JA -> words.first; UiLanguage.EN -> words.second }
    }


    public val uiLanguageTitle: String = when (language) { UiLanguage.KO -> "앱 표시 언어"; UiLanguage.JA -> "アプリの表示言語"; UiLanguage.EN -> "App language" }
    public val koreanMeaningLabel: String = when (language) { UiLanguage.KO -> "한국어 뜻"; UiLanguage.JA -> "韓国語の意味"; UiLanguage.EN -> "Korean meaning" }
    public val hangulReadingLabel: String = when (language) { UiLanguage.KO -> "한글 발음"; UiLanguage.JA -> "ハングル発音"; UiLanguage.EN -> "Hangul reading" }
    public val japaneseLabel: String = when (language) { UiLanguage.KO -> "일본어"; UiLanguage.JA -> "日本語"; UiLanguage.EN -> "Japanese" }
    public val rememberOrder: String = when (language) { UiLanguage.KO -> "주문을 기억해서 입력하세요"; UiLanguage.JA -> "注文を覚えて入力してください"; UiLanguage.EN -> "Remember and type the order" }
    public fun languageName(value: UiLanguage): String = when(value) { UiLanguage.KO -> "한국어"; UiLanguage.JA -> "日本語"; UiLanguage.EN -> "English" }
    public fun difficulty(value: String): String = when(value) {
        "RELAXED", "EASY" -> when(language) { UiLanguage.KO -> "쉬움"; UiLanguage.JA -> "かんたん"; UiLanguage.EN -> "Easy" }
        "RUSH", "HARD" -> when(language) { UiLanguage.KO -> "어려움"; UiLanguage.JA -> "むずかしい"; UiLanguage.EN -> "Hard" }
        else -> heightNormal
    }
    public fun cafeDifficultyDescription(value: CafeDifficulty): String {
        val characters = value.maxPromptCharacters
        val seconds = value.orderMs / 1000
        return when (language) {
            UiLanguage.KO -> "${characters}자 이내 단어·짧은 표현 · 첫 주문 ${seconds}초"
            UiLanguage.JA -> "${characters}文字以内の単語・短い表現 · 最初の注文 ${seconds}秒"
            UiLanguage.EN -> "Words and short phrases up to $characters characters · first order ${seconds}s"
        }
    }
    public fun availableCafeOrders(count: Int): String = when (language) {
        UiLanguage.KO -> "사용 가능한 주문 ${count}개"
        UiLanguage.JA -> "練習できる注文 ${count}件"
        UiLanguage.EN -> "$count orders available"
    }
    public fun orderCard(index: Int, total: Int): String = when(language) {
        UiLanguage.KO -> "주문  $index/$total"
        UiLanguage.JA -> "注文  $index/$total"
        UiLanguage.EN -> "ORDER  $index/$total"
    }
    public fun seconds(value: Long): String = when(language) { UiLanguage.KO -> "${value}초"; UiLanguage.JA -> "${value}秒"; UiLanguage.EN -> "${value}s" }
    public fun successes(value: Int): String = when(language) { UiLanguage.KO -> "성공 $value"; UiLanguage.JA -> "成功 $value"; UiLanguage.EN -> "Successes $value" }

    public val longPressTitle: String = when (language) { UiLanguage.KO -> "길게 누르기 기호"; UiLanguage.JA -> "長押し記号"; UiLanguage.EN -> "Long press symbols" }
    public val longPressEditInApp: String = when (language) { UiLanguage.KO -> "기호는 본체 앱의 설정에서 변경하세요."; UiLanguage.JA -> "記号はアプリの設定で変更してください。"; UiLanguage.EN -> "Edit symbols in the app’s settings." }
    public val longPressLayout: String = when (language) { UiLanguage.KO -> "기호를 편집할 배열"; UiLanguage.JA -> "記号を編集する配列"; UiLanguage.EN -> "Layout to customize" }
    public val longPressKey: String = when (language) { UiLanguage.KO -> "기호를 편집할 키"; UiLanguage.JA -> "記号を編集するキー"; UiLanguage.EN -> "Key to customize" }
    public val longPressReset: String = when (language) { UiLanguage.KO -> "이 키 초기화"; UiLanguage.JA -> "このキーをリセット"; UiLanguage.EN -> "Reset this key" }
    public val longPressNote: String = when (language) { UiLanguage.KO -> "빈 칸은 표시하지 않습니다. 된소리는 항상 먼저 표시됩니다."; UiLanguage.JA -> "空欄は表示されません。濃音は常に先頭に表示されます。"; UiLanguage.EN -> "Empty slots are hidden. Tense consonants always appear first." }
    public fun longPressSlot(index: Int): String = when (language) { UiLanguage.KO -> "기호 $index"; UiLanguage.JA -> "記号 $index"; UiLanguage.EN -> "Symbol $index" }
    public val layoutSelectionDescription: String = when (language) {
        UiLanguage.KO -> "한글 입력 배열 선택"
        UiLanguage.JA -> "ハングル入力配列を選択"
        UiLanguage.EN -> "Choose Hangul layout"
    }
    public val flickDistanceDescription: String = when (language) {
        UiLanguage.KO -> "플릭 입력 거리"
        UiLanguage.JA -> "フリック入力距離"
        UiLanguage.EN -> "Flick distance"
    }
    public val cycleTimeoutDescription: String = when (language) {
        UiLanguage.KO -> "천지인 연속 입력 시간"
        UiLanguage.JA -> "チョンジイン連続入力時間"
        UiLanguage.EN -> "Cheonjiin repeat timeout"
    }
    // App / onboarding / home
    public val appName: String = when (language) { UiLanguage.JA -> "ㅋㅋキーボード"; else -> "ㅋㅋ키보드" }
    public val homeTitle: String = when (language) { UiLanguage.KO -> "ㅋㅋ키보드"; UiLanguage.JA -> "ㅋㅋキーボード"; UiLanguage.EN -> "ㅋㅋ키보드" }
    public val onboardingSubtitle: String = when (language) { UiLanguage.KO -> "한글로 시작하는 입력과 연습"; UiLanguage.JA -> "ハングルから始める入力と練習"; UiLanguage.EN -> "Typing and practice starting with Hangul" }
    public val onboardingSteps: String = when (language) { UiLanguage.KO -> "1. 키보드를 활성화하고  2. 사용할 키보드로 선택하세요."; UiLanguage.JA -> "1. キーボードを有効にして  2. 使用するキーボードに選択してください。"; UiLanguage.EN -> "1. Enable the keyboard, 2. Select it as your keyboard." }
    public val openKeyboardSettings: String = when (language) { UiLanguage.KO -> "키보드 설정 열기"; UiLanguage.JA -> "キーボード設定を開く"; UiLanguage.EN -> "Open keyboard settings" }
    public val chooseKeyboard: String = when (language) { UiLanguage.KO -> "키보드 선택"; UiLanguage.JA -> "キーボードを選択"; UiLanguage.EN -> "Choose keyboard" }
    public val refreshStatus: String = when (language) { UiLanguage.KO -> "상태 새로고침"; UiLanguage.JA -> "状態を更新"; UiLanguage.EN -> "Refresh status" }
    public val startPractice: String = when (language) { UiLanguage.KO -> "연습 시작"; UiLanguage.JA -> "練習スタート"; UiLanguage.EN -> "Start practice" }
    public val keyboardReady: String = when (language) { UiLanguage.KO -> "키보드 사용 준비 완료"; UiLanguage.JA -> "キーボードの準備ができました"; UiLanguage.EN -> "Keyboard is ready" }
    public val keyboardNotReady: String = when (language) { UiLanguage.KO -> "키보드를 먼저 활성화하세요"; UiLanguage.JA -> "先にキーボードを有効にしてください"; UiLanguage.EN -> "Enable the keyboard first" }
    public val checkKeyboard: String = when (language) { UiLanguage.KO -> "키보드 확인"; UiLanguage.JA -> "キーボード確認"; UiLanguage.EN -> "Check keyboard" }
    public val keyboardTest: String = when (language) { UiLanguage.KO -> "키보드 테스트"; UiLanguage.JA -> "キーボードテスト"; UiLanguage.EN -> "Keyboard test" }
    public val typingPractice: String = when (language) { UiLanguage.KO -> "타자 연습"; UiLanguage.JA -> "タイピング練習"; UiLanguage.EN -> "Typing practice" }
    public val noLearningContent: String = when (language) { UiLanguage.KO -> "학습 콘텐츠를 불러오지 못했습니다. 키보드 테스트와 설정은 사용할 수 있습니다."; UiLanguage.JA -> "学習コンテンツを読み込めませんでした。キーボードテストと設定は利用できます。"; UiLanguage.EN -> "Could not load learning content. Keyboard test and settings still work." }
    public val basicPractice: String = when (language) { UiLanguage.KO -> "연습 시작"; UiLanguage.JA -> "練習スタート"; UiLanguage.EN -> "Start practice" }
    public val hangulBasicsTitle: String = when (language) { UiLanguage.KO -> "한글 기초"; UiLanguage.JA -> "ハングルの基礎"; UiLanguage.EN -> "Hangul basics" }
    public val wordPractice: String = when (language) { UiLanguage.KO -> "단어 연습"; UiLanguage.JA -> "単語練習"; UiLanguage.EN -> "Word practice" }
    public val hangulLearning: String = when (language) { UiLanguage.KO -> "한글학습"; UiLanguage.JA -> "ハングル学習"; UiLanguage.EN -> "Learn Hangul" }
    public val gamesTitle: String = when (language) { UiLanguage.KO -> "게임"; UiLanguage.JA -> "ゲーム"; UiLanguage.EN -> "Games" }
    public val rainTitle: String = when (language) { UiLanguage.KO -> "한글비"; UiLanguage.JA -> "ハングル雨"; UiLanguage.EN -> "Hangul Rain" }
    public val cafeTitle: String = when (language) { UiLanguage.KO -> "한글카페"; UiLanguage.JA -> "ハングルカフェ"; UiLanguage.EN -> "Hangul Cafe" }
    public val sentencePractice: String = when (language) { UiLanguage.KO -> "단문 연습"; UiLanguage.JA -> "短文練習"; UiLanguage.EN -> "Sentence practice" }
    public val convertPractice: String = when (language) { UiLanguage.KO -> "변환 연습"; UiLanguage.JA -> "変換練習"; UiLanguage.EN -> "Conversion practice" }
    public val practiceRecord: String = when (language) { UiLanguage.KO -> "연습 기록"; UiLanguage.JA -> "練習記録"; UiLanguage.EN -> "Practice records" }
    public val viewRecords: String = when (language) { UiLanguage.KO -> "기록 보기"; UiLanguage.JA -> "記録を見る"; UiLanguage.EN -> "View records" }

    // Settings
    public val settingsTitle: String = when (language) { UiLanguage.KO -> "설정"; UiLanguage.JA -> "設定"; UiLanguage.EN -> "Settings" }
    public val settingsSubtitle: String = when (language) { UiLanguage.KO -> "키보드와 입력 상태"; UiLanguage.JA -> "キーボードと入力状態"; UiLanguage.EN -> "Keyboard and input status" }
    public val systemKeyboardSettings: String = when (language) { UiLanguage.KO -> "시스템 키보드 설정"; UiLanguage.JA -> "システムキーボード設定"; UiLanguage.EN -> "System keyboard settings" }
    public val switchKeyboard: String = when (language) { UiLanguage.KO -> "키보드 전환"; UiLanguage.JA -> "キーボード切替"; UiLanguage.EN -> "Switch keyboard" }
    public val keyboardThemes: String = when (language) { UiLanguage.KO -> "앱 + 키보드 테마"; UiLanguage.JA -> "アプリ＋キーボードテーマ"; UiLanguage.EN -> "App + keyboard themes" }
    public val keyboardThemesSubtitle: String = when (language) { UiLanguage.KO -> "미리보고 적용해요"; UiLanguage.JA -> "プレビューして適用"; UiLanguage.EN -> "Preview and apply" }
    public val gameSound: String = when (language) { UiLanguage.KO -> "게임 소리"; UiLanguage.JA -> "ゲームサウンド"; UiLanguage.EN -> "Game sound" }
    public val haptic: String = when (language) { UiLanguage.KO -> "키 입력 햅틱"; UiLanguage.JA -> "キー入力ハプティクス"; UiLanguage.EN -> "Key haptics" }
    public val hapticNote: String = when (language) { UiLanguage.KO -> "기기와 시스템 설정에 따라 진동이 제한될 수 있습니다."; UiLanguage.JA -> "端末やシステム設定により振動が制限される場合があります。"; UiLanguage.EN -> "Vibration may be limited by device and system settings." }
    public val hangulLayout: String = when (language) { UiLanguage.KO -> "한글 입력 배열"; UiLanguage.JA -> "ハングル入力配列"; UiLanguage.EN -> "Hangul layout" }
    public val portrait: String = when (language) { UiLanguage.KO -> "세로 화면"; UiLanguage.JA -> "縦画面"; UiLanguage.EN -> "Portrait" }
    public val landscape: String = when (language) { UiLanguage.KO -> "가로 화면"; UiLanguage.JA -> "横画面"; UiLanguage.EN -> "Landscape" }
    public val keyboardHeight: String = when (language) { UiLanguage.KO -> "키보드 높이"; UiLanguage.JA -> "キーボードの高さ"; UiLanguage.EN -> "Keyboard height" }
    public val heightCompact: String = when (language) { UiLanguage.KO -> "낮게"; UiLanguage.JA -> "低め"; UiLanguage.EN -> "Compact" }
    public val heightNormal: String = when (language) { UiLanguage.KO -> "보통"; UiLanguage.JA -> "普通"; UiLanguage.EN -> "Normal" }
    public val heightTall: String = when (language) { UiLanguage.KO -> "높게"; UiLanguage.JA -> "高め"; UiLanguage.EN -> "Tall" }
    public val numberRow: String = when (language) { UiLanguage.KO -> "숫자 행"; UiLanguage.JA -> "数字行"; UiLanguage.EN -> "Number row" }
    public val advancedInput: String = when (language) { UiLanguage.KO -> "입력 세부 설정"; UiLanguage.JA -> "入力の詳細設定"; UiLanguage.EN -> "Advanced input settings" }
    public val managePhrases: String = when (language) { UiLanguage.KO -> "상용구 관리"; UiLanguage.JA -> "定型文管理"; UiLanguage.EN -> "Manage phrases" }

    // Themes
    public val themesTitle: String = when (language) { UiLanguage.KO -> "앱 + 키보드 테마"; UiLanguage.JA -> "アプリ＋キーボードテーマ"; UiLanguage.EN -> "App + keyboard themes" }
    public val themesSubtitle: String = when (language) { UiLanguage.KO -> "앱과 키보드에 함께 적용해요 · 아래는 키보드 미리보기"; UiLanguage.JA -> "アプリとキーボードに適用 · 以下はキーボードのプレビュー"; UiLanguage.EN -> "Applies to the app and keyboard · keyboard preview below" }
    public val themesEmpty: String = when (language) { UiLanguage.KO -> "테마를 불러오지 못했습니다."; UiLanguage.JA -> "テーマを読み込めませんでした。"; UiLanguage.EN -> "Could not load themes." }
    public val themeUpcoming: String = when (language) { UiLanguage.KO -> "예정"; UiLanguage.JA -> "予定"; UiLanguage.EN -> "Coming soon" }
    public val themeFollowsSystem: String = when (language) { UiLanguage.KO -> "기기 설정에 맞춰 바뀝니다"; UiLanguage.JA -> "端末設定に合わせて変わります"; UiLanguage.EN -> "Follows device settings" }
    public val themeFree: String = when (language) { UiLanguage.KO -> "무료"; UiLanguage.JA -> "無料"; UiLanguage.EN -> "Free" }
    public val themeInUse: String = when (language) { UiLanguage.KO -> "사용 중"; UiLanguage.JA -> "使用中"; UiLanguage.EN -> "In use" }
    public val themeSeoulAdCta: String = when (language) { UiLanguage.KO -> "광고 보고 24시간 쓰기"; UiLanguage.JA -> "広告を見て24時間使う"; UiLanguage.EN -> "Watch an ad for 24h access" }
    public val themeApply: String = when (language) { UiLanguage.KO -> "적용"; UiLanguage.JA -> "適用"; UiLanguage.EN -> "Apply" }
    public val themeAdPreparing: String = when (language) { UiLanguage.KO -> "광고를 준비하거나 재생 중입니다."; UiLanguage.JA -> "広告を準備または再生中です。"; UiLanguage.EN -> "Preparing or playing the ad." }
    public val themeSeoulAdBody: String = when (language) { UiLanguage.KO -> "보상형 광고를 끝까지 보면 서울 낮·밤 테마를 24시간 사용할 수 있습니다. 시간이 지나면 서울 낮은 라이트, 서울 밤은 다크로 돌아갑니다."; UiLanguage.JA -> "リワード広告を最後まで見るとソウル昼・夜テーマを24時間使えます。期限後はソウル昼はライト、ソウル夜はダークに戻ります。"; UiLanguage.EN -> "Watch the rewarded ad to the end for 24h Seoul day/night access. Afterwards Seoul day returns to Light and Seoul night to Dark." }
    public val watchAd: String = when (language) { UiLanguage.KO -> "광고 보기"; UiLanguage.JA -> "広告を見る"; UiLanguage.EN -> "Watch ad" }
    public val previewPending: String = when (language) { UiLanguage.KO -> "미리보기 준비 중"; UiLanguage.JA -> "プレビュー準備中"; UiLanguage.EN -> "Preview coming soon" }
    public val previewThemeImage: String = when (language) { UiLanguage.KO -> "테마 이미지"; UiLanguage.JA -> "テーマ画像"; UiLanguage.EN -> "Theme image" }
    public val previewQwerty: String = when (language) { UiLanguage.KO -> "QWERTY 미리보기"; UiLanguage.JA -> "QWERTYプレビュー"; UiLanguage.EN -> "QWERTY preview" }

    // Phrases
    public val phrasesTitle: String = when (language) { UiLanguage.KO -> "상용구 관리"; UiLanguage.JA -> "定型文管理"; UiLanguage.EN -> "Manage phrases" }
    public val phrasesSubtitle: String = when (language) { UiLanguage.KO -> "키보드 도구모음에서 바로 넣을 문구"; UiLanguage.JA -> "キーボードのツールバーからすぐ使える文言"; UiLanguage.EN -> "Phrases to insert from the keyboard toolbar" }
    public val newPhrase: String = when (language) { UiLanguage.KO -> "새 상용구"; UiLanguage.JA -> "新しい定型文"; UiLanguage.EN -> "New phrase" }
    public val editPhraseTitle: String = when (language) { UiLanguage.KO -> "상용구 수정"; UiLanguage.JA -> "定型文を編集"; UiLanguage.EN -> "Edit phrase" }
    public val phraseEditorHint: String = when (language) { UiLanguage.KO -> "제목 40자 · 내용 500자 이내"; UiLanguage.JA -> "タイトル40字・内容500字以内"; UiLanguage.EN -> "Title ≤ 40 chars · body ≤ 500 chars" }
    public val titleLabel: String = when (language) { UiLanguage.KO -> "제목"; UiLanguage.JA -> "タイトル"; UiLanguage.EN -> "Title" }
    public val contentLabel: String = when (language) { UiLanguage.KO -> "내용"; UiLanguage.JA -> "内容"; UiLanguage.EN -> "Body" }
    public val save: String = when (language) { UiLanguage.KO -> "저장"; UiLanguage.JA -> "保存"; UiLanguage.EN -> "Save" }
    public val edit: String = when (language) { UiLanguage.KO -> "수정"; UiLanguage.JA -> "編集"; UiLanguage.EN -> "Edit" }
    public val delete: String = when (language) { UiLanguage.KO -> "삭제"; UiLanguage.JA -> "削除"; UiLanguage.EN -> "Delete" }
    public val cancel: String = when (language) { UiLanguage.KO -> "취소"; UiLanguage.JA -> "キャンセル"; UiLanguage.EN -> "Cancel" }
    public val close: String = when (language) { UiLanguage.KO -> "닫기"; UiLanguage.JA -> "閉じる"; UiLanguage.EN -> "Close" }
    public val titleRequired: String = when (language) { UiLanguage.KO -> "제목을 입력하세요."; UiLanguage.JA -> "タイトルを入力してください。"; UiLanguage.EN -> "Enter a title." }
    public val contentRequired: String = when (language) { UiLanguage.KO -> "내용을 입력하세요."; UiLanguage.JA -> "内容を入力してください。"; UiLanguage.EN -> "Enter the body." }
    public val titleTooLong: String = when (language) { UiLanguage.KO -> "제목은 40자 이내로 입력하세요."; UiLanguage.JA -> "タイトルは40字以内で入力してください。"; UiLanguage.EN -> "Keep the title within 40 characters." }
    public val contentTooLong: String = when (language) { UiLanguage.KO -> "내용은 500자 이내로 입력하세요."; UiLanguage.JA -> "内容は500字以内で入力してください。"; UiLanguage.EN -> "Keep the body within 500 characters." }
    public val phraseSaveFailed: String = when (language) { UiLanguage.KO -> "상용구를 저장하지 못했습니다."; UiLanguage.JA -> "定型文を保存できませんでした。"; UiLanguage.EN -> "Could not save the phrase." }
    public val phraseDeleteFailed: String = when (language) { UiLanguage.KO -> "상용구를 삭제하지 못했습니다."; UiLanguage.JA -> "定型文を削除できませんでした。"; UiLanguage.EN -> "Could not delete the phrase." }
    public val deletePhraseTitle: String = when (language) { UiLanguage.KO -> "상용구 삭제"; UiLanguage.JA -> "定型文の削除"; UiLanguage.EN -> "Delete phrase" }
    public val deletePhraseBody: String = when (language) { UiLanguage.KO -> "이 상용구를 삭제할까요?"; UiLanguage.JA -> "この定型文を削除しますか？"; UiLanguage.EN -> "Delete this phrase?" }

    // Test / select
    public val testTitle: String = when (language) { UiLanguage.KO -> "키보드 테스트"; UiLanguage.JA -> "キーボードテスト"; UiLanguage.EN -> "Keyboard test" }
    public val testSubtitle: String = when (language) { UiLanguage.KO -> "아래 칸에 입력해 보세요"; UiLanguage.JA -> "下の欄に入力してみてください"; UiLanguage.EN -> "Try typing in the box below" }
    public val inputTestLabel: String = when (language) { UiLanguage.KO -> "입력 테스트"; UiLanguage.JA -> "入力テスト"; UiLanguage.EN -> "Input test" }
    public val clear: String = when (language) { UiLanguage.KO -> "지우기"; UiLanguage.JA -> "クリア"; UiLanguage.EN -> "Clear" }
    public val selectBasic: String = when (language) { UiLanguage.KO -> "기본 연습"; UiLanguage.JA -> "基本練習"; UiLanguage.EN -> "Basic practice" }
    public val selectSentence: String = when (language) { UiLanguage.KO -> "단문 연습"; UiLanguage.JA -> "短文練習"; UiLanguage.EN -> "Sentence practice" }
    public val selectConvert: String = when (language) { UiLanguage.KO -> "변환 연습"; UiLanguage.JA -> "変換練習"; UiLanguage.EN -> "Conversion practice" }
    public val selectSubtitleDefault: String = when (language) { UiLanguage.KO -> "연습할 언어를 선택하세요"; UiLanguage.JA -> "練習する言語を選んでください"; UiLanguage.EN -> "Choose a language to practice" }
    public val selectSubtitleSentence: String = when (language) { UiLanguage.KO -> "세 줄로 문장을 확인하고 입력하세요"; UiLanguage.JA -> "3行で文を確認して入力してください"; UiLanguage.EN -> "Check the sentence in three lines and type it" }
    public val sentenceRandomNote: String = when (language) { UiLanguage.KO -> "입력할 문장의 언어는 매번 무작위로 정해집니다."; UiLanguage.JA -> "入力する文の言語は毎回ランダムに決まります。"; UiLanguage.EN -> "The sentence language is chosen randomly each time." }
    public val difficultyLabel: String = when (language) { UiLanguage.KO -> "난이도"; UiLanguage.JA -> "難易度"; UiLanguage.EN -> "Difficulty" }
    public val start: String = when (language) { UiLanguage.KO -> "시작"; UiLanguage.JA -> "スタート"; UiLanguage.EN -> "Start" }
    public val noSentencesForMode: String = when (language) { UiLanguage.KO -> "이 모드에서 사용할 학습 문장이 없습니다."; UiLanguage.JA -> "このモードで使える学習文がありません。"; UiLanguage.EN -> "No learning sentences for this mode." }

    // Practice / games
    public val answerLabelDefault: String = when (language) { UiLanguage.KO -> "정답 입력"; UiLanguage.JA -> "答えを入力"; UiLanguage.EN -> "Type the answer" }
    public val rainAnswerLabel: String = when (language) { UiLanguage.KO -> "떨어지는 단어의 답"; UiLanguage.JA -> "落ちてくる単語の答え"; UiLanguage.EN -> "Answer for the falling word" }
    public val cafeOrderLabel: String = when (language) { UiLanguage.KO -> "주문 입력"; UiLanguage.JA -> "注文を入力"; UiLanguage.EN -> "Type the order" }
    public val answerCorrect: String = when (language) { UiLanguage.KO -> "정답!"; UiLanguage.JA -> "正解！"; UiLanguage.EN -> "Correct!" }
    public val rainHit: String = when (language) { UiLanguage.KO -> "적중!"; UiLanguage.JA -> "命中！"; UiLanguage.EN -> "Hit!" }
    public val rainMiss: String = when (language) { UiLanguage.KO -> "다시 시도"; UiLanguage.JA -> "もう一度"; UiLanguage.EN -> "Try again" }
    public val confirm: String = when (language) { UiLanguage.KO -> "확인"; UiLanguage.JA -> "確認"; UiLanguage.EN -> "Check" }
    public val submitInput: String = when (language) { UiLanguage.KO -> "입력"; UiLanguage.JA -> "入力"; UiLanguage.EN -> "Enter" }
    public val submit: String = when (language) { UiLanguage.KO -> "제출"; UiLanguage.JA -> "提出"; UiLanguage.EN -> "Submit" }
    public val finishPractice: String = when (language) { UiLanguage.KO -> "연습 끝내기"; UiLanguage.JA -> "練習を終える"; UiLanguage.EN -> "Finish practice" }
    public val endGame: String = when (language) { UiLanguage.KO -> "게임 끝내기"; UiLanguage.JA -> "ゲームを終える"; UiLanguage.EN -> "End game" }
    public val leaveCafe: String = when (language) { UiLanguage.KO -> "카페 나가기"; UiLanguage.JA -> "カフェを出る"; UiLanguage.EN -> "Leave cafe" }
    public val orderSuccess: String = when (language) { UiLanguage.KO -> "주문 성공!"; UiLanguage.JA -> "注文成功！"; UiLanguage.EN -> "Order complete!" }
    public val orderNext: String = when (language) { UiLanguage.KO -> "다음 주문으로 넘어갑니다"; UiLanguage.JA -> "次の注文に進みます"; UiLanguage.EN -> "Moving to the next order" }
    public val timeUp: String = when (language) { UiLanguage.KO -> "시간 초과"; UiLanguage.JA -> "時間切れ"; UiLanguage.EN -> "Time's up" }
    public val typeAsShown: String = when (language) { UiLanguage.KO -> "보이는 대로 입력하세요"; UiLanguage.JA -> "見たまま入力してください"; UiLanguage.EN -> "Type exactly as shown" }
    public val jaInputHint: String = when (language) { UiLanguage.KO -> "일본어 그대로 또는 한글 발음으로 입력하세요"; UiLanguage.JA -> "日本語のまま、またはハングル発音で入力してください"; UiLanguage.EN -> "Type the Japanese as-is or its Hangul reading" }

    // Result / progress / finishing
    public val resultTitle: String = when (language) { UiLanguage.KO -> "연습 결과"; UiLanguage.JA -> "練習結果"; UiLanguage.EN -> "Results" }
    public val resultSubtitle: String = when (language) { UiLanguage.KO -> "수고했어요!"; UiLanguage.JA -> "お疲れさまでした！"; UiLanguage.EN -> "Nice work!" }
    public val retry: String = when (language) { UiLanguage.KO -> "다시 하기"; UiLanguage.JA -> "もう一度"; UiLanguage.EN -> "Try again" }
    public val finishingDone: String = when (language) { UiLanguage.KO -> "연습을 마쳤어요."; UiLanguage.JA -> "練習が終わりました。"; UiLanguage.EN -> "Practice finished." }
    public val progressTitle: String = when (language) { UiLanguage.KO -> "내 기록"; UiLanguage.JA -> "マイ記録"; UiLanguage.EN -> "My records" }
    public val progressSubtitle: String = when (language) { UiLanguage.KO -> "꾸준한 연습이 실력이 됩니다"; UiLanguage.JA -> "コツコツ練習が実力になります"; UiLanguage.EN -> "Steady practice builds skill" }

    // Dialog / chrome / audio
    public val back: String = when (language) { UiLanguage.KO -> "‹ 뒤로"; UiLanguage.JA -> "‹ 戻る"; UiLanguage.EN -> "‹ Back" }
    public val backDescription: String = when (language) { UiLanguage.KO -> "뒤로"; UiLanguage.JA -> "戻る"; UiLanguage.EN -> "Back" }
    public val sound: String = when (language) { UiLanguage.KO -> "소리"; UiLanguage.JA -> "サウンド"; UiLanguage.EN -> "Sound" }
    public val soundSettingsDescription: String = when (language) { UiLanguage.KO -> "게임 소리 설정"; UiLanguage.JA -> "ゲームサウンド設定"; UiLanguage.EN -> "Game sound settings" }
    public val bgm: String = when (language) { UiLanguage.KO -> "배경음악"; UiLanguage.JA -> "BGM"; UiLanguage.EN -> "Music" }
    public val sfx: String = when (language) { UiLanguage.KO -> "효과음"; UiLanguage.JA -> "効果音"; UiLanguage.EN -> "Effects" }
    public val bgmDescription: String = when (language) { UiLanguage.KO -> "게임 배경음악"; UiLanguage.JA -> "ゲームBGM"; UiLanguage.EN -> "Game music" }
    public val sfxDescription: String = when (language) { UiLanguage.KO -> "게임 효과음"; UiLanguage.JA -> "ゲーム効果音"; UiLanguage.EN -> "Game effects" }

    // Modes / status / misc
    public val modeKorean: String = when (language) { UiLanguage.KO -> "한국어 입력"; UiLanguage.JA -> "韓国語入力"; UiLanguage.EN -> "Korean input" }
    public val modeJapanese: String = when (language) { UiLanguage.KO -> "일본어·한글 발음"; UiLanguage.JA -> "日本語・ハングル発音"; UiLanguage.EN -> "Japanese–Hangul reading" }
    public val styleBasic: String = when (language) { UiLanguage.KO -> "기본 연습"; UiLanguage.JA -> "基本練習"; UiLanguage.EN -> "Basic practice" }
    public val styleSentence: String = when (language) { UiLanguage.KO -> "단문 연습"; UiLanguage.JA -> "短文練習"; UiLanguage.EN -> "Sentence practice" }
    public val styleConvert: String = when (language) { UiLanguage.KO -> "변환 연습"; UiLanguage.JA -> "変換練習"; UiLanguage.EN -> "Conversion practice" }
    public val statusUnknown: String = when (language) { UiLanguage.KO -> "확인 전"; UiLanguage.JA -> "未確認"; UiLanguage.EN -> "Unknown" }
    public val statusDisabled: String = when (language) { UiLanguage.KO -> "비활성화"; UiLanguage.JA -> "無効"; UiLanguage.EN -> "Disabled" }
    public val statusEnabled: String = when (language) { UiLanguage.KO -> "활성화됨"; UiLanguage.JA -> "有効"; UiLanguage.EN -> "Enabled" }
    public val statusActive: String = when (language) { UiLanguage.KO -> "사용 중"; UiLanguage.JA -> "使用中"; UiLanguage.EN -> "Active" }

    /** Fixed-dictionary lookup for a known Korean static label. Unknown keys return input. */
    public fun text(korean: String): String {
        if (language == UiLanguage.KO) return korean
        val table = if (language == UiLanguage.JA) JA_TEXT else EN_TEXT
        return table[korean] ?: korean
    }

    // Typed templates for dynamic labels (no user content passes through a regex).
    public fun pageTitle(pageId: String): String = when (pageId) {
        "ONBOARDING" -> when (language) { UiLanguage.KO -> "시작"; UiLanguage.JA -> "スタート"; UiLanguage.EN -> "Start" }
        "HOME" -> when (language) { UiLanguage.KO -> "홈"; UiLanguage.JA -> "ホーム"; UiLanguage.EN -> "Home" }
        "SETTINGS" -> settingsTitle
        "THEMES" -> themesTitle
        "ADVANCED_SETTINGS" -> advancedInput
        "PHRASES" -> phrasesTitle
        "PHRASE_EDITOR" -> when (language) { UiLanguage.KO -> "상용구 편집"; UiLanguage.JA -> "定型文編集"; UiLanguage.EN -> "Edit phrases" }
        "TEST" -> testTitle
        "SELECT" -> when (language) { UiLanguage.KO -> "연습 선택"; UiLanguage.JA -> "練習選択"; UiLanguage.EN -> "Choose practice" }
        "JAMO" -> hangulLearning
        "PRACTICE" -> typingPractice
        "RAIN" -> rainTitle
        "CAFE" -> cafeTitle
        "FINISHING", "RESULT" -> resultTitle
        "PROGRESS" -> progressTitle
        else -> when (language) { UiLanguage.KO -> "홈"; UiLanguage.JA -> "ホーム"; UiLanguage.EN -> "Home" }
    }

    public fun statusLine(status: String): String {
        val label = when (status) {
            "UNKNOWN" -> statusUnknown
            "DISABLED" -> statusDisabled
            "ENABLED" -> statusEnabled
            "ACTIVE" -> statusActive
            else -> statusUnknown
        }
        return when (language) { UiLanguage.KO -> "현재 상태: $label"; UiLanguage.JA -> "現在の状態: $label"; UiLanguage.EN -> "Status: $label" }
    }

    public fun homeRecordLine(sessions: Int, bestAccuracy: Int, xp: Int): String = when (language) {
        UiLanguage.KO -> "완료 ${sessions}회  ·  최고 정확도 ${bestAccuracy}%  ·  총 ${xp} XP"
        UiLanguage.JA -> "完了${sessions}回  ·  最高正解率${bestAccuracy}%  ·  合計${xp} XP"
        UiLanguage.EN -> "$sessions done  ·  best $bestAccuracy%  ·  $xp XP total"
    }

    public fun savedPhrases(count: Int, max: Int = 50): String = when (language) {
        UiLanguage.KO -> "저장된 상용구 $count / $max"
        UiLanguage.JA -> "保存済み定型文 $count / $max"
        UiLanguage.EN -> "Saved phrases $count / $max"
    }

    public fun phraseLength(titleLen: Int, contentLen: Int): String = when (language) {
        UiLanguage.KO -> "제목 ${titleLen}/40 · 내용 ${contentLen}/500"
        UiLanguage.JA -> "タイトル ${titleLen}/40 · 内容 ${contentLen}/500"
        UiLanguage.EN -> "Title ${titleLen}/40 · body ${contentLen}/500"
    }

    public fun testChars(count: Int): String = when (language) {
        UiLanguage.KO -> "${count}자 입력"
        UiLanguage.JA -> "${count}文字入力"
        UiLanguage.EN -> "$count chars typed"
    }

    public fun availableSentences(count: Int): String = when (language) {
        UiLanguage.KO -> "사용 가능한 문장 ${count}개"
        UiLanguage.JA -> "利用可能な文 ${count}件"
        UiLanguage.EN -> "$count sentences available"
    }

    public fun practiceHeader(index: Int, total: Int, score: Int): String = when (language) {
        UiLanguage.KO -> "$index / $total  ·  점수 $score"
        UiLanguage.JA -> "$index / $total  ·  スコア $score"
        UiLanguage.EN -> "$index / $total  ·  score $score"
    }

    public fun answerRetry(expected: String): String = when (language) {
        UiLanguage.KO -> "다시 확인해 보세요 · $expected"
        UiLanguage.JA -> "もう一度確認してください · $expected"
        UiLanguage.EN -> "Check again · $expected"
    }

    public fun rainHud(seconds: Long, lives: Int, score: Int): String = when (language) {
        UiLanguage.KO -> "${seconds}초  ·  생명 $lives  ·  점수 $score"
        UiLanguage.JA -> "${seconds}秒  ·  ライフ $lives  ·  スコア $score"
        UiLanguage.EN -> "${seconds}s  ·  lives $lives  ·  score $score"
    }

    public fun cafeOrder(index: Int, total: Int, score: Int): String = when (language) {
        UiLanguage.KO -> "주문 $index / $total  ·  점수 $score"
        UiLanguage.JA -> "注文 $index / $total  ·  スコア $score"
        UiLanguage.EN -> "Order $index / $total  ·  score $score"
    }

    public fun resultScore(score: Int): String = when (language) {
        UiLanguage.KO -> "${score}점"
        UiLanguage.JA -> "${score}点"
        UiLanguage.EN -> "$score pts"
    }

    public fun resultAccuracy(accuracy: Int, cpm: Int): String = when (language) {
        UiLanguage.KO -> "정확도 ${accuracy}%  ·  분당 ${cpm}타"
        UiLanguage.JA -> "正解率 ${accuracy}%  ·  毎分 ${cpm}打"
        UiLanguage.EN -> "Accuracy $accuracy%  ·  $cpm CPM"
    }

    public fun resultCombo(maxCombo: Int, errors: Int): String = when (language) {
        UiLanguage.KO -> "최대 콤보 $maxCombo  ·  오류 $errors"
        UiLanguage.JA -> "最大コンボ $maxCombo  ·  ミス $errors"
        UiLanguage.EN -> "Max combo $maxCombo  ·  errors $errors"
    }

    public fun resultXp(xp: Int): String = when (language) {
        UiLanguage.KO -> "획득 XP $xp"
        UiLanguage.JA -> "獲得XP $xp"
        UiLanguage.EN -> "Earned XP $xp"
    }

    public fun totalXp(xp: Int): String = when (language) {
        UiLanguage.KO -> "총 XP  $xp"
        UiLanguage.JA -> "合計XP  $xp"
        UiLanguage.EN -> "Total XP  $xp"
    }

    public fun completedSessions(count: Int): String = when (language) {
        UiLanguage.KO -> "완료한 세션  $count"
        UiLanguage.JA -> "完了セッション  $count"
        UiLanguage.EN -> "Sessions done  $count"
    }

    public fun bestAccuracy(value: Int): String = when (language) {
        UiLanguage.KO -> "최고 정확도  ${value}%"
        UiLanguage.JA -> "最高正解率  ${value}%"
        UiLanguage.EN -> "Best accuracy  $value%"
    }

    public fun rainHighScore(score: Int): String = when (language) {
        UiLanguage.KO -> "${rainTitle} 최고 점수  $score"
        UiLanguage.JA -> "${rainTitle}最高スコア  $score"
        UiLanguage.EN -> "${rainTitle} high score  $score"
    }

    public fun flickDistanceLabel(dp: Int): String = when (language) {
        UiLanguage.KO -> "플릭 입력 거리 · ${dp}dp"
        UiLanguage.JA -> "フリック入力距離 · ${dp}dp"
        UiLanguage.EN -> "Flick distance · ${dp}dp"
    }

    public fun cycleTimeoutLabel(ms: Int): String = when (language) {
        UiLanguage.KO -> "천지인 연속 입력 시간 · ${ms}ms"
        UiLanguage.JA -> "チョンジイン連続入力時間 · ${ms}ms"
        UiLanguage.EN -> "Cheonjiin repeat timeout · ${ms}ms"
    }

    public fun trialRemaining(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val h = totalSeconds / 3600L
        val m = (totalSeconds % 3600L) / 60L
        val s = totalSeconds % 60L
        return when (language) {
            UiLanguage.KO -> "${h}시간 ${m}분 ${s}초 남음"
            UiLanguage.JA -> "残り${h}時間${m}分${s}秒"
            UiLanguage.EN -> "${h}h ${m}m ${s}s left"
        }
    }

    public fun seoulLockedNote(millis: Long): String = when (language) {
        UiLanguage.KO -> trialRemaining(millis)
        UiLanguage.JA -> trialRemaining(millis)
        UiLanguage.EN -> trialRemaining(millis)
    }

    public fun seoulAdTitle(themeTitle: String): String = when (language) {
        UiLanguage.KO -> "${themeTitle} 테마"
        UiLanguage.JA -> "${themeTitle}テーマ"
        UiLanguage.EN -> "$themeTitle theme"
    }

    public fun themePreviewDescription(titleKo: String, available: Boolean): String {
        val base = text(titleKo)
        return when (language) {
            UiLanguage.KO -> if (available) "${base} 키보드 미리보기" else "${base} 테마 시안"
            UiLanguage.JA -> if (available) "${base}キーボードプレビュー" else "${base}テーマ案"
            UiLanguage.EN -> if (available) "$base keyboard preview" else "$base theme mock"
        }
    }

    public fun sentenceJaHint(copyText: String): String = when (language) {
        UiLanguage.KO -> "한글 발음으로 입력하고 일본어 후보를 확정하세요: $copyText"
        UiLanguage.JA -> "ハングル発音で入力し日本語候補を確定してください: $copyText"
        UiLanguage.EN -> "Type the Hangul reading and confirm the Japanese candidate: $copyText"
    }

    public fun sentenceKoHint(copyText: String): String = when (language) {
        UiLanguage.KO -> "한국어 문장을 그대로 입력하세요: $copyText"
        UiLanguage.JA -> "韓国語の文をそのまま入力してください: $copyText"
        UiLanguage.EN -> "Type the Korean sentence as-is: $copyText"
    }

    private companion object {
        private val JA_TEXT: Map<String, String> = mapOf(
            "한글로 시작하는 입력과 연습" to "ハングルから始める入力と練習",
            "1. 키보드를 활성화하고  2. 사용할 키보드로 선택하세요." to "1. キーボードを有効にして  2. 使用するキーボードに選択してください。",
            "키보드 설정 열기" to "キーボード設定を開く",
            "키보드 선택" to "キーボードを選択",
            "상태 새로고침" to "状態を更新",
            "연습 시작" to "練習スタート",
            "한글학습" to "ハングル学習",
            "게임" to "ゲーム",
            "한글비" to "ハングル雨",
            "한글카페" to "ハングルカフェ",
            "ㅋㅋ키보드" to "ㅋㅋキーボード",
            "키보드 사용 준비 완료" to "キーボードの準備ができました",
            "키보드를 먼저 활성화하세요" to "先にキーボードを有効にしてください",
            "키보드 확인" to "キーボード確認",
            "키보드 테스트" to "キーボードテスト",
            "타자 연습" to "タイピング練習",
            "학습 콘텐츠를 불러오지 못했습니다. 키보드 테스트와 설정은 사용할 수 있습니다." to "学習コンテンツを読み込めませんでした。キーボードテストと設定は利用できます。",
            "단문 연습" to "短文練習",
            "변환 연습" to "変換練習",
            "연습 기록" to "練習記録",
            "기록 보기" to "記録を見る",
            "설정" to "設定",
            "키보드와 입력 상태" to "キーボードと入力状態",
            "시스템 키보드 설정" to "システムキーボード設定",
            "키보드 전환" to "キーボード切替",
            "앱 + 키보드 테마" to "アプリ＋キーボードテーマ",
            "미리보고 적용해요" to "プレビューして適用",
            "게임 소리" to "ゲームサウンド",
            "키 입력 햅틱" to "キー入力ハプティクス",
            "기기와 시스템 설정에 따라 진동이 제한될 수 있습니다." to "端末やシステム設定により振動が制限される場合があります。",
            "한글 입력 배열" to "ハングル入力配列",
            "세로 화면" to "縦画面",
            "가로 화면" to "横画面",
            "키보드 높이" to "キーボードの高さ",
            "낮게" to "低め",
            "보통" to "普通",
            "높게" to "高め",
            "숫자 행" to "数字行",
            "입력 세부 설정" to "入力の詳細設定",
            "상용구 관리" to "定型文管理",
            "앱과 키보드에 함께 적용해요 · 아래는 키보드 미리보기" to "アプリとキーボードに適用 · 以下はキーボードのプレビュー",
            "테마를 불러오지 못했습니다." to "テーマを読み込めませんでした。",
            "예정" to "予定",
            "기기 설정에 맞춰 바뀝니다" to "端末設定に合わせて変わります",
            "광고를 보면 24시간 이용" to "広告を見ると24時間利用",
            "무료" to "無料",
            "사용 중" to "使用中",
            "광고 보고 24시간 쓰기" to "広告を見て24時間使う",
            "적용" to "適用",
            "광고를 준비하거나 재생 중입니다." to "広告を準備または再生中です。",
            "키보드 도구모음에서 바로 넣을 문구" to "キーボードのツールバーからすぐ使える文言",
            "새 상용구" to "新しい定型文",
            "상용구 수정" to "定型文を編集",
            "제목 40자 · 내용 500자 이내" to "タイトル40字・内容500字以内",
            "제목" to "タイトル",
            "내용" to "内容",
            "저장" to "保存",
            "수정" to "編集",
            "삭제" to "削除",
            "취소" to "キャンセル",
            "닫기" to "閉じる",
            "제목을 입력하세요." to "タイトルを入力してください。",
            "내용을 입력하세요." to "内容を入力してください。",
            "제목은 40자 이내로 입력하세요." to "タイトルは40字以内で入力してください。",
            "내용은 500자 이내로 입력하세요." to "内容は500字以内で入力してください。",
            "상용구를 저장하지 못했습니다." to "定型文を保存できませんでした。",
            "상용구를 삭제하지 못했습니다." to "定型文を削除できませんでした。",
            "상용구 삭제" to "定型文の削除",
            "이 상용구를 삭제할까요?" to "この定型文を削除しますか？",
            "아래 칸에 입력해 보세요" to "下の欄に入力してみてください",
            "입력 테스트" to "入力テスト",
            "지우기" to "クリア",
            "기본 연습" to "基本練習",
            "세 줄로 문장을 확인하고 입력하세요" to "3行で文を確認して入力してください",
            "연습할 언어를 선택하세요" to "練習する言語を選んでください",
            "입력할 문장의 언어는 매번 무작위로 정해집니다." to "入力する文の言語は毎回ランダムに決まります。",
            "난이도" to "難易度",
            "시작" to "スタート",
            "이 모드에서 사용할 학습 문장이 없습니다." to "このモードで使える学習文がありません。",
            "정답 입력" to "答えを入力",
            "떨어지는 단어의 답" to "落ちてくる単語の答え",
            "주문 입력" to "注文を入力",
            "정답!" to "正解！",
            "적중!" to "命中！",
            "다시 시도" to "もう一度",
            "확인" to "確認",
            "입력" to "入力",
            "제출" to "提出",
            "연습 끝내기" to "練習を終える",
            "게임 끝내기" to "ゲームを終える",
            "카페 나가기" to "カフェを出る",
            "주문 성공!" to "注文成功！",
            "다음 주문으로 넘어갑니다" to "次の注文に進みます",
            "시간 초과" to "時間切れ",
            "보이는 대로 입력하세요" to "見たまま入力してください",
            "일본어 그대로 또는 한글 발음으로 입력하세요" to "日本語のまま、またはハングル発音で入力してください",
            "연습 결과" to "練習結果",
            "수고했어요!" to "お疲れさまでした！",
            "다시 하기" to "もう一度",
            "연습을 마쳤어요." to "練習が終わりました。",
            "내 기록" to "マイ記録",
            "꾸준한 연습이 실력이 됩니다" to "コツコツ練習が実力になります",
            "‹ 뒤로" to "‹ 戻る",
            "뒤로" to "戻る",
            "소리" to "サウンド",
            "게임 소리 설정" to "ゲームサウンド設定",
            "배경음악" to "BGM",
            "효과음" to "効果音",
            "게임 배경음악" to "ゲームBGM",
            "게임 효과음" to "ゲーム効果音",
            "한국어 입력" to "韓国語入力",
            "일본어·한글 발음" to "日本語・ハングル発音",
            "변환 연습" to "変換練習",
            "확인 전" to "未確認",
            "비활성화" to "無効",
            "활성화됨" to "有効",
            "시작" to "スタート",
            "홈" to "ホーム",
            "상용구 편집" to "定型文編集",
            "연습 선택" to "練習選択",
            "카페 게임" to "カフェゲーム",
            "미리보기 준비 중" to "プレビュー準備中",
            "테마 이미지" to "テーマ画像",
            "QWERTY 미리보기" to "QWERTYプレビュー",
            "시스템" to "システム",
            "라이트" to "ライト",
            "다크" to "ダーク",
            "서울 낮" to "ソウル昼",
            "서울 밤" to "ソウル夜",
            "천지인" to "チョンジイン",
            "천지인 플러스" to "チョンジインプラス",
            "한글 플릭" to "ハングルフリック",
            "보상형 광고를 끝까지 보면 서울 낮·밤 테마를 24시간 사용할 수 있습니다. 시간이 지나면 서울 낮은 라이트, 서울 밤은 다크로 돌아갑니다." to "リワード広告を最後まで見るとソウル昼・夜テーマを24時間使えます。期限後はソウル昼はライト、ソウル夜はダークに戻ります。",
            "광고 보기" to "広告を見る",
            "서울의 낮" to "ソウルの昼",
            "서울의 밤" to "ソウルの夜",
            "태극" to "太極",
            "부산의 밤" to "釜山の夜",
            "제주" to "済州",
        )
        private val EN_TEXT: Map<String, String> = mapOf(
            "한글로 시작하는 입력과 연습" to "Typing and practice starting with Hangul",
            "1. 키보드를 활성화하고  2. 사용할 키보드로 선택하세요." to "1. Enable the keyboard, 2. Select it as your keyboard.",
            "키보드 설정 열기" to "Open keyboard settings",
            "키보드 선택" to "Choose keyboard",
            "상태 새로고침" to "Refresh status",
            "연습 시작" to "Start practice",
            "한글학습" to "Learn Hangul",
            "게임" to "Games",
            "한글비" to "Hangul Rain",
            "한글카페" to "Hangul Cafe",
            "ㅋㅋ키보드" to "ㅋㅋ키보드",
            "키보드 사용 준비 완료" to "Keyboard is ready",
            "키보드를 먼저 활성화하세요" to "Enable the keyboard first",
            "키보드 확인" to "Check keyboard",
            "키보드 테스트" to "Keyboard test",
            "타자 연습" to "Typing practice",
            "학습 콘텐츠를 불러오지 못했습니다. 키보드 테스트와 설정은 사용할 수 있습니다." to "Could not load learning content. Keyboard test and settings still work.",
            "단문 연습" to "Sentence practice",
            "변환 연습" to "Conversion practice",
            "연습 기록" to "Practice records",
            "기록 보기" to "View records",
            "설정" to "Settings",
            "키보드와 입력 상태" to "Keyboard and input status",
            "시스템 키보드 설정" to "System keyboard settings",
            "키보드 전환" to "Switch keyboard",
            "앱 + 키보드 테마" to "App + keyboard themes",
            "미리보고 적용해요" to "Preview and apply",
            "게임 소리" to "Game sound",
            "키 입력 햅틱" to "Key haptics",
            "기기와 시스템 설정에 따라 진동이 제한될 수 있습니다." to "Vibration may be limited by device and system settings.",
            "한글 입력 배열" to "Hangul layout",
            "세로 화면" to "Portrait",
            "가로 화면" to "Landscape",
            "키보드 높이" to "Keyboard height",
            "낮게" to "Compact",
            "보통" to "Normal",
            "높게" to "Tall",
            "숫자 행" to "Number row",
            "입력 세부 설정" to "Advanced input settings",
            "상용구 관리" to "Manage phrases",
            "앱과 키보드에 함께 적용해요 · 아래는 키보드 미리보기" to "Applies to the app and keyboard · keyboard preview below",
            "테마를 불러오지 못했습니다." to "Could not load themes.",
            "예정" to "Coming soon",
            "기기 설정에 맞춰 바뀝니다" to "Follows device settings",
            "광고를 보면 24시간 이용" to "24h access with an ad",
            "무료" to "Free",
            "사용 중" to "In use",
            "광고 보고 24시간 쓰기" to "Watch an ad for 24h access",
            "적용" to "Apply",
            "광고를 준비하거나 재생 중입니다." to "Preparing or playing the ad.",
            "키보드 도구모음에서 바로 넣을 문구" to "Phrases to insert from the keyboard toolbar",
            "새 상용구" to "New phrase",
            "상용구 수정" to "Edit phrase",
            "제목 40자 · 내용 500자 이내" to "Title ≤ 40 chars · body ≤ 500 chars",
            "제목" to "Title",
            "내용" to "Body",
            "저장" to "Save",
            "수정" to "Edit",
            "삭제" to "Delete",
            "취소" to "Cancel",
            "닫기" to "Close",
            "제목을 입력하세요." to "Enter a title.",
            "내용을 입력하세요." to "Enter the body.",
            "제목은 40자 이내로 입력하세요." to "Keep the title within 40 characters.",
            "내용은 500자 이내로 입력하세요." to "Keep the body within 500 characters.",
            "상용구를 저장하지 못했습니다." to "Could not save the phrase.",
            "상용구를 삭제하지 못했습니다." to "Could not delete the phrase.",
            "상용구 삭제" to "Delete phrase",
            "이 상용구를 삭제할까요?" to "Delete this phrase?",
            "아래 칸에 입력해 보세요" to "Try typing in the box below",
            "입력 테스트" to "Input test",
            "지우기" to "Clear",
            "기본 연습" to "Basic practice",
            "세 줄로 문장을 확인하고 입력하세요" to "Check the sentence in three lines and type it",
            "연습할 언어를 선택하세요" to "Choose a language to practice",
            "입력할 문장의 언어는 매번 무작위로 정해집니다." to "The sentence language is chosen randomly each time.",
            "난이도" to "Difficulty",
            "시작" to "Start",
            "이 모드에서 사용할 학습 문장이 없습니다." to "No learning sentences for this mode.",
            "정답 입력" to "Type the answer",
            "떨어지는 단어의 답" to "Answer for the falling word",
            "주문 입력" to "Type the order",
            "정답!" to "Correct!",
            "적중!" to "Hit!",
            "다시 시도" to "Try again",
            "확인" to "Check",
            "입력" to "Enter",
            "제출" to "Submit",
            "연습 끝내기" to "Finish practice",
            "게임 끝내기" to "End game",
            "카페 나가기" to "Leave cafe",
            "주문 성공!" to "Order complete!",
            "다음 주문으로 넘어갑니다" to "Moving to the next order",
            "시간 초과" to "Time's up",
            "보이는 대로 입력하세요" to "Type exactly as shown",
            "일본어 그대로 또는 한글 발음으로 입력하세요" to "Type the Japanese as-is or its Hangul reading",
            "연습 결과" to "Results",
            "수고했어요!" to "Nice work!",
            "다시 하기" to "Try again",
            "연습을 마쳤어요." to "Practice finished.",
            "내 기록" to "My records",
            "꾸준한 연습이 실력이 됩니다" to "Steady practice builds skill",
            "‹ 뒤로" to "‹ Back",
            "뒤로" to "Back",
            "소리" to "Sound",
            "게임 소리 설정" to "Game sound settings",
            "배경음악" to "Music",
            "효과음" to "Effects",
            "게임 배경음악" to "Game music",
            "게임 효과음" to "Game effects",
            "한국어 입력" to "Korean input",
            "일본어·한글 발음" to "Japanese–Hangul reading",
            "변환 연습" to "Conversion practice",
            "확인 전" to "Unknown",
            "비활성화" to "Disabled",
            "활성화됨" to "Enabled",
            "시작" to "Start",
            "홈" to "Home",
            "상용구 편집" to "Edit phrases",
            "연습 선택" to "Choose practice",
            "카페 게임" to "Cafe game",
            "미리보기 준비 중" to "Preview coming soon",
            "테마 이미지" to "Theme image",
            "QWERTY 미리보기" to "QWERTY preview",
            "시스템" to "System",
            "라이트" to "Light",
            "다크" to "Dark",
            "서울 낮" to "Seoul day",
            "서울 밤" to "Seoul night",
            "천지인" to "Cheonjiin",
            "천지인 플러스" to "Cheonjiin Plus",
            "한글 플릭" to "Hangul flick",
            "보상형 광고를 끝까지 보면 서울 낮·밤 테마를 24시간 사용할 수 있습니다. 시간이 지나면 서울 낮은 라이트, 서울 밤은 다크로 돌아갑니다." to "Watch the rewarded ad to the end for 24h Seoul day/night access. Afterwards Seoul day returns to Light and Seoul night to Dark.",
            "광고 보기" to "Watch ad",
            "서울의 낮" to "Seoul day",
            "서울의 밤" to "Seoul night",
            "태극" to "Taegeuk",
            "부산의 밤" to "Busan night",
            "제주" to "Jeju",
        )
    }
}
