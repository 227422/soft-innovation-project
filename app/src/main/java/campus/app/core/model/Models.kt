package campus.app.core.model

/**
 * 界面用到的数据结构。
 *
 * 原则（契约第 2 节）：
 * - 未知一律用 null 或 UNKNOWN，绝不用 0、空串或"符合"代替；
 * - 时间点带时区；纯日期保留 precision；
 * - 前端显示后端给的顺序与分数，不重算、不自己判定资格。
 */

enum class Category(val label: String) {
    COMPETITION("竞赛"),
    POLICY("政策"),
    PUBLICITY("公示"),
    DAILY("日常"),
    OTHER("其他"),
    UNKNOWN("待确认");

    companion object {
        fun from(raw: String?): Category = when (raw?.lowercase()) {
            "competition" -> COMPETITION
            "policy" -> POLICY
            "publicity" -> PUBLICITY
            "daily" -> DAILY
            "other" -> OTHER
            else -> UNKNOWN
        }
    }
}

enum class ImportanceLevel(val number: Int) {
    L1(1), L2(2), L3(3), L4(4), UNKNOWN(0);

    companion object {
        fun from(raw: Int?): ImportanceLevel =
            entries.firstOrNull { it.number == raw } ?: UNKNOWN
    }
}

enum class MatchState {
    RELEVANT, POSSIBLE, IRRELEVANT, UNKNOWN;

    companion object {
        fun from(raw: String?): MatchState = when (raw?.lowercase()) {
            "relevant" -> RELEVANT
            "possible" -> POSSIBLE
            "irrelevant" -> IRRELEVANT
            else -> UNKNOWN
        }
    }
}

/** 时间事实的精度：有明确时刻 / 只有日期 / 未知。 */
enum class DatePrecision {
    EXACT, DATE_ONLY, UNKNOWN;

    companion object {
        fun from(raw: String?): DatePrecision = when (raw?.lowercase()) {
            "exact", "datetime", "time" -> EXACT
            "date" -> DATE_ONLY
            else -> UNKNOWN
        }
    }
}

/** 紧迫度对外状态。注意 CLOSED 与 UNKNOWN 不能混用。 */
enum class UrgencyState {
    EXACT, DATE_ONLY, UNKNOWN, NONE, CLOSED;

    companion object {
        fun from(raw: String?): UrgencyState = when (raw?.lowercase()) {
            "exact" -> EXACT
            "date", "date_only" -> DATE_ONLY
            "none" -> NONE
            "closed", "expired" -> CLOSED
            else -> UNKNOWN
        }
    }
}

/**
 * 截止时间。
 * [iso] 已确认的截止时间点；[precision] 区分"当日 17:00"与"仅到日期"；
 * [daysLeft] 后端计算的剩余天数，UI 只显示不重算。
 */
data class DeadlineFact(
    val iso: String? = null,
    val precision: DatePrecision = DatePrecision.UNKNOWN,
    val urgency: UrgencyState = UrgencyState.UNKNOWN,
    val daysLeft: Int? = null,
    val displayText: String? = null,
)

/** 重要性。tentative=true 表示结论待确认，界面必须显示出来。 */
data class ImportanceInfo(
    val level: ImportanceLevel = ImportanceLevel.UNKNOWN,
    val label: String? = null,
    val reasons: List<String> = emptyList(),
    val tentative: Boolean = false,
    val impactScore: Double? = null,
    val method: String? = null,
)

/** 个人匹配。资格核对单独返回，不在这里下结论。 */
data class MatchInfo(
    val state: MatchState = MatchState.UNKNOWN,
    val reasons: List<String> = emptyList(),
    val missingFields: List<String> = emptyList(),
)

/** 三维排序结果。前端只显示，不重算。 */
data class RankingInfo(
    val compositeScore: Double? = null,
    val rank: Int? = null,
    val reasons: List<String> = emptyList(),
    val strategyVersion: String? = null,
) {
    /** 综合分展示文本；未知时显示"—"，不显示 0。 */
    val scoreText: String
        get() = compositeScore?.let { "%.1f".format(it) } ?: "—"
}

data class FeedbackInfo(
    val starred: Boolean = false,
    val ignored: Boolean = false,
)

/** 列表项。 */
data class NoticeItem(
    val noticeId: String,
    val title: String,
    val category: Category = Category.UNKNOWN,
    val sourceName: String? = null,
    val publishedAt: String? = null,
    val deadline: DeadlineFact = DeadlineFact(),
    val importance: ImportanceInfo = ImportanceInfo(),
    val match: MatchInfo = MatchInfo(),
    val ranking: RankingInfo = RankingInfo(),
    val feedback: FeedbackInfo = FeedbackInfo(),
    val contentVersion: String? = null,
    val latestVersion: String? = null,
    val titleOnly: Boolean = false,
) {
    /** 内容版本落后于最新版本时提示"有更新"。 */
    val hasUpdate: Boolean
        get() = contentVersion != null && latestVersion != null && contentVersion != latestVersion
}

data class FeedPage(
    val items: List<NoticeItem> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int = 0,
    val profileVersion: Int? = null,
    val evaluatedAt: String? = null,
    val lastUpdatedAt: String? = null,
    val queryFingerprint: String? = null,
    val rulesVersion: String? = null,
    val rankingStrategyVersion: String? = null,
)

/** 四问卡片的内容。缺依据时 [pending] 为 true，界面必须显示"待确认"。 */
data class FourQuestions(
    val shouldJoin: QuestionAnswer = QuestionAnswer(),
    val missing: QuestionAnswer = QuestionAnswer(checklist = emptyList()),
    val daysLeft: QuestionAnswer = QuestionAnswer(),
    val nextStep: QuestionAnswer = QuestionAnswer(),
)

data class QuestionAnswer(
    val answer: String? = null,
    val pending: Boolean = false,
    val checklist: List<String> = emptyList(),
)

data class PolicyRef(
    val title: String? = null,
    val clause: String? = null,
    val updatedAt: String? = null,
    val url: String? = null,
)

data class Explanation(
    val summary: String? = null,
    val four: FourQuestions = FourQuestions(),
    val materials: List<String> = emptyList(),
    val policies: List<PolicyRef> = emptyList(),
    val terms: List<String> = emptyList(),
    val stale: Boolean = false,
    val status: String? = null,
)

/** 通知详情：正文 + 解释状态。正文不等待 AI。 */
data class NoticeDetail(
    val item: NoticeItem,
    val body: String? = null,
    val sourceUrl: String? = null,
    val attachments: List<AttachmentInfo> = emptyList(),
    val explanation: Explanation? = null,
    val explanationStatus: String? = null,
)

data class AttachmentInfo(
    val name: String? = null,
    val url: String? = null,
    val status: String? = null,
)

data class CatalogNode(val id: String, val name: String)

data class GradeOption(val value: String, val label: String)

data class Profile(
    val schoolId: String? = null,
    val schoolName: String? = null,
    val collegeId: String? = null,
    val collegeName: String? = null,
    val majorId: String? = null,
    val majorName: String? = null,
    val grade: Int? = null,
    val educationStage: String? = null,
    val majorGroupName: String? = null,
    val version: Int = 0,
) {
    /** 资料是否足够支撑匹配。 */
    val isComplete: Boolean
        get() = schoolId != null && collegeId != null && majorId != null && grade != null
}

data class Preferences(
    val courseIds: List<String> = emptyList(),
    val skillIds: List<String> = emptyList(),
    val goalIds: List<String> = emptyList(),
    val interestIds: List<String> = emptyList(),
    val version: Int = 0,
)

data class FeedbackState(
    val noticeId: String,
    val starred: Boolean = false,
    val ignored: Boolean = false,
    val version: Int = 0,
)

data class Reminder(
    val reminderId: String? = null,
    val noticeId: String,
    val eventId: String,
    val triggerAt: String? = null,
    val enabled: Boolean = true,
    val note: String? = null,
)
