package campus.app.core.network

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
import campus.app.core.session.SessionView
import kotlinx.serialization.json.JsonObject

/**
 * JSON → 模型的映射。
 *
 * 后端尚未冻结 openapi，因此这里对字段名做有限兼容：
 * - 时间字段同时接受 expires_at / expiresAt；
 * - 列表同时接受 items / notices / data.items。
 * 兼容只针对命名风格，不伪造缺失值。
 */
object Mappers {

    fun session(o: JsonObject): SessionView? {
        val user = o.obj("user") ?: o
        val userId = user.str("id") ?: user.str("user_id") ?: return null
        return SessionView(
            userId = userId,
            email = user.str("email").orEmpty(),
            expiresAtEpochSeconds = o.long("expires_at") ?: o.long("expiresAt") ?: 0L,
            csrfToken = o.str("csrf_token") ?: o.str("csrfToken").orEmpty(),
        )
    }

    fun deadline(o: JsonObject?): DeadlineFact {
        if (o == null) return DeadlineFact()
        val node = o.obj("deadline") ?: o.obj("time") ?: o
        return DeadlineFact(
            iso = node.str("iso") ?: node.str("value") ?: node.str("at"),
            precision = DatePrecision.from(node.str("precision")),
            urgency = UrgencyState.from(node.str("urgency") ?: node.str("state")),
            daysLeft = node.int("days_left") ?: node.int("daysLeft"),
            displayText = node.str("display") ?: node.str("text"),
        )
    }

    fun importance(o: JsonObject?): ImportanceInfo {
        if (o == null) return ImportanceInfo()
        val level = ImportanceLevel.from(o.int("level"))
        return ImportanceInfo(
            level = level,
            label = o.str("label"),
            reasons = o.strList("reasons"),
            tentative = o.bool("tentative") ?: (level == ImportanceLevel.UNKNOWN),
            impactScore = o.double("impact_score"),
            method = o.str("method"),
        )
    }

    fun match(o: JsonObject?): MatchInfo {
        if (o == null) return MatchInfo()
        return MatchInfo(
            state = MatchState.from(o.str("state") ?: o.str("match")),
            reasons = o.strList("reasons"),
            missingFields = (o.obj("eligibility")?.strList("missing_fields")) ?: o.strList("missing_fields"),
        )
    }

    fun ranking(o: JsonObject?): RankingInfo {
        if (o == null) return RankingInfo()
        return RankingInfo(
            compositeScore = o.double("composite_score") ?: o.double("score"),
            rank = o.int("rank"),
            reasons = o.strList("reasons"),
            strategyVersion = o.str("strategy_version"),
        )
    }

    fun feedback(o: JsonObject?): FeedbackInfo {
        if (o == null) return FeedbackInfo()
        return FeedbackInfo(
            starred = o.bool("starred") ?: false,
            ignored = o.bool("ignored") ?: false,
        )
    }

    fun noticeItem(o: JsonObject): NoticeItem? {
        val id = o.str("notice_id") ?: o.str("id") ?: return null
        val rankingNode = o.obj("ranking")
        return NoticeItem(
            noticeId = id,
            title = o.str("title").orEmpty().ifBlank { "（无标题）" },
            category = Category.from(o.str("category")),
            sourceName = o.str("source_name"),
            publishedAt = o.str("published_at") ?: o.str("created_at"),
            deadline = deadline(o.obj("deadline") ?: o),
            importance = importance(o.obj("importance")),
            match = match(o.obj("match")),
            ranking = ranking(rankingNode),
            feedback = feedback(o.obj("feedback")),
            contentVersion = o.str("content_version"),
            latestVersion = o.str("latest_version"),
            titleOnly = o.str("status") == "title_only" || (o.bool("title_only") ?: false),
        )
    }

    fun feed(o: JsonObject): FeedPage = FeedPage(
        items = o.items().mapNotNull { noticeItem(it) },
        total = o.int("total") ?: 0,
        offset = o.int("offset") ?: 0,
        limit = o.int("limit") ?: 0,
        profileVersion = o.int("profile_version"),
        evaluatedAt = o.str("evaluated_at"),
        lastUpdatedAt = o.str("last_updated_at"),
        queryFingerprint = o.str("query_fingerprint"),
        rulesVersion = o.str("rules_version"),
        rankingStrategyVersion = o.str("ranking_strategy_version"),
    )

    private fun question(o: JsonObject?): QuestionAnswer {
        if (o == null) return QuestionAnswer(pending = true)
        return QuestionAnswer(
            answer = o.str("answer") ?: o.str("text"),
            pending = o.bool("pending") ?: (o.str("status") == "unknown"),
            checklist = o.strList("checklist"),
        )
    }

    fun explanation(o: JsonObject): Explanation {
        val four = o.obj("four_questions") ?: o.obj("four")
        val questions = if (four == null) {
            FourQuestions()
        } else {
            FourQuestions(
                shouldJoin = question(four.obj("should_join")),
                missing = question(four.obj("missing")),
                daysLeft = question(four.obj("days_left")),
                nextStep = question(four.obj("next_step")),
            )
        }
        return Explanation(
            summary = o.str("summary"),
            four = questions,
            materials = o.strList("materials"),
            policies = o.objList("policies").mapNotNull { p ->
                val title = p.str("title") ?: return@mapNotNull null
                PolicyRef(
                    title = title,
                    clause = p.str("clause"),
                    updatedAt = p.str("updated_at"),
                    url = p.str("url"),
                )
            },
            terms = o.strList("terms"),
            stale = o.bool("stale") ?: false,
            status = o.str("status"),
        )
    }

    fun detail(o: JsonObject): NoticeDetail? {
        val item = noticeItem(o.obj("notice") ?: o) ?: return null
        return NoticeDetail(
            item = item,
            body = o.str("body") ?: o.str("content") ?: o.str("text"),
            sourceUrl = o.str("source_url") ?: o.str("url"),
            attachments = o.objList("attachments").mapNotNull { a ->
                val name = a.str("name") ?: a.str("filename") ?: return@mapNotNull null
                AttachmentInfo(name = name, url = a.str("url"), status = a.str("status"))
            },
            explanation = o.obj("explanation")?.let { explanation(it) },
            explanationStatus = o.str("explanation_status"),
        )
    }

    fun catalog(o: JsonObject): List<CatalogNode> =
        o.items().mapNotNull { n ->
            val id = n.str("id") ?: n.str("value") ?: return@mapNotNull null
            CatalogNode(id = id, name = n.str("name") ?: n.str("label") ?: id)
        }

    fun grades(o: JsonObject): List<GradeOption> =
        o.items().mapNotNull { n ->
            val value = n.str("value") ?: n.str("id") ?: return@mapNotNull null
            GradeOption(value = value, label = n.str("label") ?: n.str("name") ?: value)
        }

    fun profile(o: JsonObject): Profile = Profile(
        schoolId = o.str("school_id"),
        schoolName = o.str("school_name"),
        collegeId = o.str("college_id"),
        collegeName = o.str("college_name"),
        majorId = o.str("major_id"),
        majorName = o.str("major_name"),
        grade = o.int("grade"),
        educationStage = o.str("education_stage"),
        majorGroupName = o.obj("major_group")?.str("name"),
        version = o.int("version") ?: 0,
    )

    fun preferences(o: JsonObject): Preferences = Preferences(
        courseIds = o.strList("course_ids"),
        skillIds = o.strList("skill_ids"),
        goalIds = o.strList("goal_ids"),
        interestIds = o.strList("interest_ids"),
        version = o.int("version") ?: 0,
    )

    fun feedbackState(o: JsonObject): FeedbackState? {
        val id = o.str("notice_id") ?: o.str("id") ?: return null
        return FeedbackState(
            noticeId = id,
            starred = o.bool("starred") ?: false,
            ignored = o.bool("ignored") ?: false,
            version = o.int("version") ?: 0,
        )
    }

    fun feedbackList(o: JsonObject): List<FeedbackState> =
        o.items().mapNotNull { feedbackState(it) }

    fun reminder(o: JsonObject): Reminder? {
        val noticeId = o.str("notice_id") ?: return null
        val eventId = o.str("event_id") ?: return null
        return Reminder(
            reminderId = o.str("reminder_id") ?: o.str("id"),
            noticeId = noticeId,
            eventId = eventId,
            triggerAt = o.str("trigger_at"),
            enabled = o.bool("enabled") ?: true,
            note = o.str("note"),
        )
    }

    fun reminders(o: JsonObject): List<Reminder> = o.items().mapNotNull { reminder(it) }

    /** 解析 {"error":{"code","message","request_id"}}。 */
    fun errorEnvelope(o: JsonObject): Triple<String?, String?, String?> {
        val e = o.obj("error") ?: o
        return Triple(e.str("code"), e.str("message"), e.str("request_id"))
    }
}
