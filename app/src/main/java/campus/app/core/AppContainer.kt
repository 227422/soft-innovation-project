package campus.app.core

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import campus.app.core.data.FeedbackRepository
import campus.app.core.data.MockFeedbackRepository
import campus.app.core.data.MockNoticeRepository
import campus.app.core.data.MockProfileRepository
import campus.app.core.data.MockReminderRepository
import campus.app.core.data.NoticeRepository
import campus.app.core.data.ProfileRepository
import campus.app.core.data.RealFeedbackRepository
import campus.app.core.data.RealNoticeRepository
import campus.app.core.data.RealProfileRepository
import campus.app.core.data.RealReminderRepository
import campus.app.core.data.ReminderRepository
import campus.app.core.network.ApiClient
import campus.app.core.network.Env
import campus.app.core.network.PersistentCookieJar
import campus.app.core.session.CsrfHolder
import campus.app.core.session.FakeSessionRepository
import campus.app.core.session.SessionRepository
import campus.app.core.session.SessionRepositoryImpl
import campus.app.core.session.SessionState
import campus.app.core.storage.KeyValueStore
import campus.app.core.storage.PrefsKeyValueStore

/**
 * 依赖装配。
 *
 * 只此一处根据 [Env] 决定用样例数据还是真实接口，
 * 页面拿到的永远是同样的 Repository 接口。
 * 这也是"禁止请求失败后偷偷改用假数据"的落点：
 * 降级只能通过显式切换环境发生。
 */
class AppContainer(context: Context) {

    val store: KeyValueStore = PrefsKeyValueStore(context)

    val csrf: CsrfHolder = CsrfHolder(store)

    val cookieJar: PersistentCookieJar = PersistentCookieJar(store)

    private val api: ApiClient = ApiClient(
        baseUrl = Env.baseUrl,
        cookieJar = cookieJar,
        csrfProvider = { csrf.token },
    )

    val session: SessionRepository

    val profile: ProfileRepository

    val notice: NoticeRepository

    val feedback: FeedbackRepository

    val reminders: ReminderRepository

    init {
        if (Env.usesMockData) {
            session = FakeSessionRepository()
            profile = MockProfileRepository()
            notice = MockNoticeRepository()
            feedback = MockFeedbackRepository()
            reminders = MockReminderRepository()
        } else {
            val realSession = SessionRepositoryImpl(api, cookieJar, csrf)
            session = realSession
            profile = RealProfileRepository(api)
            notice = RealNoticeRepository(
                api = api,
                store = store,
                envName = Env.current.name,
                userIdProvider = {
                    (session.state.value as? SessionState.LoggedIn)?.session?.userId
                },
            )
            feedback = RealFeedbackRepository(api)
            reminders = RealReminderRepository(api)
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer 未提供：请在 MainActivity 中用 CompositionLocalProvider 注入")
}
