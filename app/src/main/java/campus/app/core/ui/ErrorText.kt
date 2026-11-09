package campus.app.core.ui

import campus.app.core.network.ApiError

/**
 * 错误码 → 用户可读文案。
 *
 * 集中一处，保证同一个错误在所有页面说法一致；
 * 也保证"会话失效"和"网络失败"不会用同一句话，避免误导用户去改密码。
 */
fun ApiError.describe(): String = when (this) {
    ApiError.InvalidCredentials -> "邮箱或密码不正确"
    ApiError.SessionInvalid -> "登录状态已失效，请重新登录"
    ApiError.CsrfInvalid -> "安全校验已失效，请重试一次"
    ApiError.IdempotencyConflict -> "该操作已提交过，请刷新后确认结果"
    is ApiError.VersionConflict -> "内容已被更新，请刷新后再操作"
    is ApiError.Validation ->
        if (fields.isEmpty()) "输入不合法：$message" else "输入不合法：${fields.joinToString("、")}"
    ApiError.RateLimited -> "操作过于频繁，请稍后再试"
    is ApiError.Unavailable -> "网络不可用，请检查网络后重试"
    is ApiError.Unknown -> "服务返回了无法识别的结果，请稍后重试"
}
