package app.notch.domain

import app.notch.R

/**
 * Things that go without anyone noticing: how a baby holds your finger, the starfish sleep, the
 * mixed-up words. Each has the months it usually lasts and how best to keep it.
 */
enum class Keep { PHOTO, VIDEO, VOICE }

class Fade(val key: String, val from: Int, val to: Int, val keep: Keep, val title: Int)

enum class FadeState {
    /** Its months are now. */
    NOW,
    /** Kept: a picture, a clip or a word answers it. */
    KEPT,
    /** Its months are over and nothing was kept; it stays, greyed, in case a picture from then turns up. */
    PASSED,
    /** Still to come. */
    LATER,
}

object Fading {
    val all = listOf(
        Fade("grip", 0, 6, Keep.PHOTO, R.string.fade_grip),
        Fade("feet", 0, 6, Keep.PHOTO, R.string.fade_feet),
        Fade("yawn", 0, 5, Keep.VIDEO, R.string.fade_yawn),
        Fade("sleep_arms", 0, 12, Keep.PHOTO, R.string.fade_sleep_arms),
        Fade("coo", 1, 6, Keep.VOICE, R.string.fade_coo),
        Fade("milk", 0, 10, Keep.PHOTO, R.string.fade_milk),
        Fade("laugh", 3, 12, Keep.VIDEO, R.string.fade_laugh),
        Fade("toes", 4, 10, Keep.PHOTO, R.string.fade_toes),
        Fade("crawl", 6, 13, Keep.VIDEO, R.string.fade_crawl),
        Fade("babble", 6, 15, Keep.VOICE, R.string.fade_babble),
        Fade("high_chair", 6, 30, Keep.PHOTO, R.string.fade_high_chair),
        Fade("steps", 9, 18, Keep.VIDEO, R.string.fade_steps),
        Fade("waddle", 11, 30, Keep.VIDEO, R.string.fade_waddle),
        Fade("first_words", 10, 24, Keep.VOICE, R.string.fade_first_words),
        Fade("arms_up", 9, 36, Keep.PHOTO, R.string.fade_arms_up),
        Fade("dummy", 0, 36, Keep.PHOTO, R.string.fade_dummy),
        Fade("pram", 0, 36, Keep.PHOTO, R.string.fade_pram),
        Fade("starfish", 12, 60, Keep.PHOTO, R.string.fade_starfish),
        Fade("hide", 18, 60, Keep.VIDEO, R.string.fade_hide),
        Fade("mixed_words", 18, 60, Keep.VOICE, R.string.fade_mixed_words),
        Fade("scribble", 18, 48, Keep.PHOTO, R.string.fade_scribble),
        Fade("dance", 18, 84, Keep.VIDEO, R.string.fade_dance),
        Fade("bath", 6, 72, Keep.PHOTO, R.string.fade_bath),
        Fade("carry", 12, 72, Keep.PHOTO, R.string.fade_carry),
        Fade("song", 24, 84, Keep.VOICE, R.string.fade_song),
        Fade("why", 30, 72, Keep.VOICE, R.string.fade_why),
        Fade("night_visit", 24, 96, Keep.PHOTO, R.string.fade_night_visit),
        Fade("shoulders", 24, 96, Keep.PHOTO, R.string.fade_shoulders),
        Fade("story", 24, 108, Keep.PHOTO, R.string.fade_story),
        Fade("run_hug", 24, 120, Keep.VIDEO, R.string.fade_run_hug),
        Fade("hand_street", 24, 120, Keep.PHOTO, R.string.fade_hand_street),
        Fade("costume", 36, 96, Keep.PHOTO, R.string.fade_costume),
        Fade("magic", 36, 108, Keep.VOICE, R.string.fade_magic),
        Fade("training_wheels", 36, 84, Keep.VIDEO, R.string.fade_training_wheels),
        Fade("backwards_letters", 48, 84, Keep.PHOTO, R.string.fade_backwards_letters),
        Fade("gap_smile", 60, 96, Keep.PHOTO, R.string.fade_gap_smile),
        Fade("syllables", 60, 96, Keep.VOICE, R.string.fade_syllables),
        Fade("lap", 0, 96, Keep.PHOTO, R.string.fade_lap),
        Fade("toy", 12, 108, Keep.PHOTO, R.string.fade_toy),
        Fade("room", 60, 156, Keep.PHOTO, R.string.fade_room),
        Fade("grown_talk", 72, 156, Keep.VOICE, R.string.fade_grown_talk),
        Fade("hands", 0, 156, Keep.PHOTO, R.string.fade_hands),
    )

    fun byKey(key: String): Fade? = all.firstOrNull { it.key == key }

    /** A few months' warning: what is about to start is shown as "now" a little early. */
    private const val AHEAD = 1

    fun state(f: Fade, months: Int, kept: Boolean): FadeState = when {
        kept -> FadeState.KEPT
        months > f.to -> FadeState.PASSED
        months + AHEAD >= f.from -> FadeState.NOW
        else -> FadeState.LATER
    }

    /** Months left before it usually goes; 0 means this is its last month. */
    fun left(f: Fade, months: Int): Int = (f.to - months).coerceAtLeast(0)
}

/** The birthday interview: the same questions every year, answered on video. */
object Interview {
    val questions = listOf(
        R.string.q_name, R.string.q_grow_up, R.string.q_food, R.string.q_friend, R.string.q_afraid, R.string.q_laugh,
        R.string.q_best_at, R.string.q_toy, R.string.q_happy, R.string.q_travel, R.string.q_parents, R.string.q_wish,
    )

    /** The year of life an answer belongs to: answers from a fortnight before the birthday count for the new year. */
    fun yearOf(born: java.time.LocalDate, on: java.time.LocalDate): Int = Growth.age(born, on.plusDays(14)).years
}
