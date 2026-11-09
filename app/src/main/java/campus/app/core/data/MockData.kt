package campus.app.core.data

import campus.app.core.model.AttachmentInfo
import campus.app.core.model.CatalogNode
import campus.app.core.model.Category
import campus.app.core.model.DatePrecision
import campus.app.core.model.DeadlineFact
import campus.app.core.model.Explanation
import campus.app.core.model.FeedPage
import campus.app.core.model.FeedbackInfo
import campus.app.core.model.FeedbackState
import campus.app.core.model.FourQuestions
import campus.app.core.model.GradeOption
import campus.app.core.model.ImportanceInfo
import campus.app.core.model.ImportanceLevel
import campus.app.core.model.MatchInfo
import campus.app.core.model.MatchState
import campus.app.core.model.NoticeDetail
import campus.app.core.model.NoticeItem
import campus.app.core.model.PolicyRef
import campus.app.core.model.Preferences
import campus.app.core.model.Profile
import campus.app.core.model.QuestionAnswer
import campus.app.core.model.RankingInfo
import campus.app.core.model.Reminder
import campus.app.core.model.UrgencyState
import campus.app.core.network.ApiError
import campus.app.core.network.ApiResult

/**
 * 样例数据。仅当 Env 为 MOCK 时使用，界面顶部会常显"样例数据"。
 *
 * 刻意覆盖了几种边界，方便开发期自测：
 * - 截止时间只有日期 / 完全未知；
 * - 正文受限（仅标题）；
 * - 内容版本落后（有更新）；
 * - 结论待确认。
 */
object MockData {

    val schools = listOf(CatalogNode("school-sample", "示例大学"))

    val colleges = listOf(
        CatalogNode("college-cs", "计算机学院"),
        CatalogNode("college-me", "机械工程学院"),
        CatalogNode("college-em", "经济管理学院"),
    )

    val majors = listOf(
        CatalogNode("major-se", "软件工程"),
        CatalogNode("major-cs", "计算机科学与技术"),
        CatalogNode("major-ai", "人工智能"),
    )

    val grades = listOf(
        GradeOption("1", "大一"),
        GradeOption("2", "大二"),
        GradeOption("3", "大三"),
        GradeOption("4", "大四"),
        GradeOption("5", "研究生"),
    )

    var profile: Profile = Profile(
        schoolId = "school-sample",
        schoolName = "示例大学",
        collegeId = "college-cs",
        collegeName = "计算机学院",
        majorId = "major-se",
        majorName = "软件工程",
        grade = 2,
        educationStage = "undergraduate",
        majorGroupName = "计算机",
        version = 1,
    )

    var preferences: Preferences = Preferences(goalIds = listOf("goal_competition"), version = 1)

    private val feedbackMap = mutableMapOf<String, FeedbackState>()
    private val reminderMap = mutableMapOf<String, Reminder>()

    fun feedback(noticeId: String): FeedbackState =
        feedbackMap.getOrPut(noticeId) { FeedbackState(noticeId = noticeId) }

    /** 列表项只带展示用的布尔值，不比整个 FeedbackState 传出去。 */
    fun feedbackInfo(noticeId: String): FeedbackInfo {
        val state = feedback(noticeId)
        return FeedbackInfo(starred = state.starred, ignored = state.ignored)
    }

    fun saveFeedback(state: FeedbackState) {
        feedbackMap[state.noticeId] = state
    }

    fun reminders(): List<Reminder> = reminderMap.values.toList()

    fun saveReminder(reminder: Reminder) {
        reminderMap["${reminder.noticeId}:${reminder.eventId}"] = reminder
    }

    private fun item(
        id: String,
        title: String,
        category: Category,
        source: String,
        publishedAt: String,
        deadlineText: String?,
        daysLeft: Int?,
        urgency: UrgencyState,
        precision: DatePrecision,
        level: ImportanceLevel,
        levelLabel: String,
        match: MatchState,
        matchReason: String,
        score: Double,
        reasons: List<String>,
        tentative: Boolean = false,
        titleOnly: Boolean = false,
        contentVersion: String = "v1",
        latestVersion: String = "v1",
    ) = NoticeItem(
        noticeId = id,
        title = title,
        category = category,
        sourceName = source,
        publishedAt = publishedAt,
        deadline = DeadlineFact(
            iso = deadlineText,
            precision = precision,
            urgency = urgency,
            daysLeft = daysLeft,
            displayText = deadlineText,
        ),
        importance = ImportanceInfo(
            level = level,
            label = levelLabel,
            reasons = listOf(levelLabel),
            tentative = tentative,
            method = "rules",
        ),
        match = MatchInfo(state = match, reasons = listOf(matchReason)),
        ranking = RankingInfo(
            compositeScore = score,
            reasons = reasons,
            strategyVersion = "proposal-weighted-v1",
        ),
        feedback = feedbackInfo(id),
        contentVersion = contentVersion,
        latestVersion = latestVersion,
        titleOnly = titleOnly,
    )

    val notices: List<NoticeItem> = listOf(
        item(
            id = "sample-001",
            title = "第十六届大学生软件创新大赛校内选拔赛报名通知",
            category = Category.COMPETITION,
            source = "创新创业学院",
            publishedAt = "2026-11-02",
            deadlineText = "2026-12-15",
            daysLeft = 38,
            urgency = UrgencyState.DATE_ONLY,
            precision = DatePrecision.DATE_ONLY,
            level = ImportanceLevel.L1,
            levelLabel = "校级选拔，错过需再等一年",
            match = MatchState.RELEVANT,
            matchReason = "与你的专业方向一致",
            score = 78.5,
            reasons = listOf("重要（校级选拔）", "与你相关", "报名截止较远"),
        ),
        item(
            id = "sample-002",
            title = "全国大学生数学建模竞赛参赛队选拔",
            category = Category.COMPETITION,
            source = "教务处",
            publishedAt = "2026-11-05",
            deadlineText = "2026-11-09 17:00",
            daysLeft = 2,
            urgency = UrgencyState.EXACT,
            precision = DatePrecision.EXACT,
            level = ImportanceLevel.L2,
            levelLabel = "含金量较高的学科竞赛",
            match = MatchState.RELEVANT,
            matchReason = "你已修高数，符合先修要求",
            score = 71.2,
            reasons = listOf("重要", "与你相关", "后天 17:00 截止"),
        ),
        item(
            id = "sample-003",
            title = "关于本科生综合素质测评办法修订的公示",
            category = Category.POLICY,
            source = "学生工作部",
            publishedAt = "2026-11-03",
            deadlineText = null,
            daysLeft = null,
            urgency = UrgencyState.UNKNOWN,
            precision = DatePrecision.UNKNOWN,
            level = ImportanceLevel.L2,
            levelLabel = "与评奖评优直接相关",
            match = MatchState.POSSIBLE,
            matchReason = "涉及全体本科生",
            score = 52.0,
            reasons = listOf("政策类", "截止时间仍需核对"),
            tentative = true,
        ),
        item(
            id = "sample-004",
            title = "“互联网+”创新创业大赛校赛入围名单公示",
            category = Category.PUBLICITY,
            source = "创新创业学院",
            publishedAt = "2026-11-04",
            deadlineText = "2026-11-20",
            daysLeft = 13,
            urgency = UrgencyState.DATE_ONLY,
            precision = DatePrecision.DATE_ONLY,
            level = ImportanceLevel.L3,
            levelLabel = "已参赛同学需关注",
            match = MatchState.POSSIBLE,
            matchReason = "你未报名该项目，仅供知悉",
            score = 41.0,
            reasons = listOf("公示类", "看到即可，无需操作"),
            titleOnly = true,
        ),
        item(
            id = "sample-005",
            title = "图书馆冬季开放时间调整通知",
            category = Category.DAILY,
            source = "图书馆",
            publishedAt = "2026-11-06",
            deadlineText = null,
            daysLeft = null,
            urgency = UrgencyState.NONE,
            precision = DatePrecision.UNKNOWN,
            level = ImportanceLevel.L4,
            levelLabel = "日常信息",
            match = MatchState.POSSIBLE,
            matchReason = "所有在校生均适用",
            score = 22.0,
            reasons = listOf("日常类", "无需行动"),
            contentVersion = "v1",
            latestVersion = "v2",
        ),
    )

    private val explanationBase = Explanation(
        summary = "这是校内选拔赛，校级获奖可计入学科竞赛认定，并作为国赛报名依据。",
        four = FourQuestions(
            shouldJoin = QuestionAnswer(
                answer = "建议参加。与你的专业方向一致，且校级选拔不淘汰，报名即可进入国赛候选。",
            ),
            missing = QuestionAnswer(
                answer = "通知要求 4 项材料，其中 2 项需要你确认是否已具备。",
                pending = true,
                checklist = listOf("报名表（可直接填写）", "项目计划书", "可运行源码", "AI 辅助编程记录"),
            ),
            daysLeft = QuestionAnswer(
                answer = "截止 2026-12-15（当日截止），还有 38 天。",
            ),
            nextStep = QuestionAnswer(
                answer = "先确认 3 名队员与 1 名指导老师，再下载报名表填写。",
            ),
        ),
        materials = listOf("报名表", "项目计划书", "源码 zip", "AI 交互记录"),
        policies = listOf(
            PolicyRef(
                title = "计算机学院本科生综合测评实施细则（试行）",
                clause = "第三章 第 5 条：校级学科竞赛获奖计 0.5 学分",
                updatedAt = "2024-09",
                url = "https://example.invalid/policy/1",
            ),
        ),
        terms = listOf("综测：综合素质测评，与评奖评优挂钩"),
        stale = false,
        status = "ready",
    )

    val explanations: Map<String, Explanation> = mapOf(
        "sample-001" to explanationBase,
        "sample-002" to explanationBase.copy(
            summary = "学科竞赛选拔，需要先通过校内测试。",
            four = FourQuestions(
                shouldJoin = QuestionAnswer("若你有建模基础，值得一试；时间紧，需评估近期课业压力。"),
                missing = QuestionAnswer("选拔需通过校内测试，测试时间尚未公布。", pending = true),
                daysLeft = QuestionAnswer("截止 2026-11-09 17:00，还有 2 天，需尽快报名。"),
                nextStep = QuestionAnswer("今天内确认是否组队，并报名校内测试。"),
            ),
            policies = emptyList(),
            stale = false,
        ),
    )

    fun explanationFor(noticeId: String): Explanation =
        explanations[noticeId] ?: Explanation(
            summary = null,
            four = FourQuestions(
                shouldJoin = QuestionAnswer(pending = true),
                missing = QuestionAnswer(pending = true),
                daysLeft = QuestionAnswer(pending = true),
                nextStep = QuestionAnswer(pending = true),
            ),
            status = "pending",
        )

    fun detailFor(noticeId: String): NoticeDetail? {
        val base = notices.firstOrNull { it.noticeId == noticeId } ?: return null
        return NoticeDetail(
            item = base.copy(feedback = feedbackInfo(noticeId)),
            body = if (base.titleOnly) null else "（样例正文）本通知说明报名条件、材料清单与截止时间，具体以原文为准。",
            sourceUrl = "https://example.invalid/notice/$noticeId",
            attachments = listOf(
                AttachmentInfo(name = "附件1：报名表.xlsx", url = "https://example.invalid/a1", status = "ok"),
                AttachmentInfo(name = "附件2：评分细则.pdf", url = "https://example.invalid/a2", status = "ok"),
            ),
            explanation = explanationFor(noticeId),
            explanationStatus = "ready",
        )
    }
}

/** 样例模式的四个 Repository。全部不联网。 */
class MockProfileRepository : ProfileRepository {
    override suspend fun schools(prefix: String?) =
        ApiResult.Success(MockData.schools.filter { prefix.isNullOrBlank() || it.name.contains(prefix) })

    override suspend fun colleges(schoolId: String) = ApiResult.Success(MockData.colleges)

    override suspend fun majors(schoolId: String, collegeId: String) = ApiResult.Success(MockData.majors)

    override suspend fun grades() = ApiResult.Success(MockData.grades)

    override suspend fun load() = ApiResult.Success(MockData.profile)

    override suspend fun save(profile: Profile): ApiResult<Profile> {
        val next = profile.copy(version = MockData.profile.version + 1)
        MockData.profile = next
        return ApiResult.Success(next)
    }

    override suspend fun loadPreferences() = ApiResult.Success(MockData.preferences)

    override suspend fun savePreferences(preferences: Preferences): ApiResult<Preferences> {
        val next = preferences.copy(version = MockData.preferences.version + 1)
        MockData.preferences = next
        return ApiResult.Success(next)
    }
}

class MockNoticeRepository : NoticeRepository {

    override suspend fun feed(category: Category?, offset: Int, limit: Int): FeedResult {
        val all = if (category == null) MockData.notices else MockData.notices.filter { it.category == category }
        // 用真实反馈状态覆盖，保证星标/忽略后列表能反映出来
        val items = all.drop(offset).take(limit).map { it.copy(feedback = MockData.feedbackInfo(it.noticeId)) }
        return FeedResult.Fresh(
            FeedPage(
                items = items,
                total = all.size,
                offset = offset,
                limit = limit,
                profileVersion = MockData.profile.version,
                evaluatedAt = "2026-11-07T16:00:00+08:00",
                lastUpdatedAt = "2026-11-07T15:56:00+08:00",
                rankingStrategyVersion = "proposal-weighted-v1",
            ),
        )
    }

    override suspend fun detail(noticeId: String): DetailResult {
        val detail = MockData.detailFor(noticeId)
            ?: return DetailResult.Failed(ApiError.Unknown("not_found", "找不到该通知", null))
        return DetailResult.Fresh(detail)
    }

    override fun cachedFeed(category: Category?): FeedPage? = null

    override fun cachedDetail(noticeId: String): NoticeDetail? = null
}

class MockFeedbackRepository : FeedbackRepository {

    override suspend fun get(noticeId: String) = ApiResult.Success(MockData.feedback(noticeId))

    override suspend fun setStarred(noticeId: String, starred: Boolean): ApiResult<FeedbackState> {
        val current = MockData.feedback(noticeId)
        val next = current.copy(
            starred = starred,
            version = current.version + 1,
            // 星标与忽略互斥：星标时自动取消忽略
            ignored = if (starred) false else current.ignored,
        )
        MockData.saveFeedback(next)
        return ApiResult.Success(next)
    }

    override suspend fun setIgnored(noticeId: String, ignored: Boolean): ApiResult<FeedbackState> {
        val current = MockData.feedback(noticeId)
        val next = current.copy(
            ignored = ignored,
            version = current.version + 1,
            starred = if (ignored) false else current.starred,
        )
        MockData.saveFeedback(next)
        return ApiResult.Success(next)
    }

    override suspend fun list(state: String): ApiResult<List<FeedbackState>> {
        val all = MockData.notices.map { MockData.feedback(it.noticeId) }
        return ApiResult.Success(
            when (state) {
                "starred" -> all.filter { it.starred }
                "ignored" -> all.filter { it.ignored }
                else -> all
            },
        )
    }
}

class MockReminderRepository : ReminderRepository {

    override suspend fun list() = ApiResult.Success(MockData.reminders())

    override suspend fun setReminder(
        noticeId: String,
        eventId: String,
        triggerAt: String?,
        enabled: Boolean,
    ): ApiResult<Reminder> {
        val reminder = Reminder(
            reminderId = "$noticeId:$eventId",
            noticeId = noticeId,
            eventId = eventId,
            triggerAt = triggerAt,
            enabled = enabled,
        )
        MockData.saveReminder(reminder)
        return ApiResult.Success(reminder)
    }
}
