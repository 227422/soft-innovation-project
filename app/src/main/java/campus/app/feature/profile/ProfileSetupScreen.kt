package campus.app.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import campus.app.core.LocalAppContainer
import campus.app.core.model.CatalogNode
import campus.app.core.model.GradeOption
import campus.app.core.model.Profile
import campus.app.core.network.ApiResult
import campus.app.core.ui.describe
import kotlinx.coroutines.launch

private enum class PickerField { SCHOOL, COLLEGE, MAJOR, GRADE }

/**
 * 资料页：学校 → 学院 → 专业 → 年级。
 *
 * 契约要点：
 * - 学院/专业只能选不能填；
 * - 上级变更只清空正在编辑的下级，不提前写库；
 * - 保存失败保留草稿；
 * - 专业大类由后端归纳，前端不猜。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onDone: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    var schools by remember { mutableStateOf<List<CatalogNode>>(emptyList()) }
    var colleges by remember { mutableStateOf<List<CatalogNode>>(emptyList()) }
    var majors by remember { mutableStateOf<List<CatalogNode>>(emptyList()) }
    var grades by remember { mutableStateOf<List<GradeOption>>(emptyList()) }

    var school by remember { mutableStateOf<CatalogNode?>(null) }
    var college by remember { mutableStateOf<CatalogNode?>(null) }
    var major by remember { mutableStateOf<CatalogNode?>(null) }
    var grade by remember { mutableStateOf<GradeOption?>(null) }
    var majorGroup by remember { mutableStateOf<String?>(null) }

    var version by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf<PickerField?>(null) }

    // 首次进入：并行拉目录与已有资料
    LaunchedEffect(Unit) {
        loading = true
        when (val g = container.profile.grades()) {
            is ApiResult.Success -> grades = g.value
            is ApiResult.Failure -> error = g.error.describe()
        }
        when (val s = container.profile.schools()) {
            is ApiResult.Success -> schools = s.value
            is ApiResult.Failure -> error = s.error.describe()
        }
        when (val p = container.profile.load()) {
            is ApiResult.Success -> {
                val profile = p.value
                version = profile.version
                majorGroup = profile.majorGroupName
                school = profile.schoolId?.let { id -> schools.firstOrNull { it.id == id } }
                    ?: profile.schoolId?.let { CatalogNode(it, profile.schoolName.orEmpty()) }
                grade = profile.grade?.let { g -> grades.firstOrNull { it.value == g.toString() } }
            }
            is ApiResult.Failure -> error = p.error.describe()
        }
        loading = false
    }

    fun loadColleges(schoolId: String, preselect: String? = null) {
        scope.launch {
            when (val r = container.profile.colleges(schoolId)) {
                is ApiResult.Success -> {
                    colleges = r.value
                    college = preselect?.let { id -> r.value.firstOrNull { it.id == id } }
                }
                is ApiResult.Failure -> error = r.error.describe()
            }
        }
    }

    fun loadMajors(schoolId: String, collegeId: String, preselect: String? = null) {
        scope.launch {
            when (val r = container.profile.majors(schoolId, collegeId)) {
                is ApiResult.Success -> {
                    majors = r.value
                    major = preselect?.let { id -> r.value.firstOrNull { it.id == id } }
                }
                is ApiResult.Failure -> error = r.error.describe()
            }
        }
    }

    fun save() {
        val currentSchool = school ?: run { error = "请先选择学校"; return }
        val currentCollege = college ?: run { error = "请先选择学院"; return }
        val currentMajor = major ?: run { error = "请先选择专业"; return }
        val currentGrade = grade ?: run { error = "请先选择年级"; return }

        saving = true
        error = null
        scope.launch {
            val profile = Profile(
                schoolId = currentSchool.id,
                schoolName = currentSchool.name,
                collegeId = currentCollege.id,
                collegeName = currentCollege.name,
                majorId = currentMajor.id,
                majorName = currentMajor.name,
                grade = currentGrade.value.toIntOrNull(),
                version = version,
            )
            when (val r = container.profile.save(profile)) {
                is ApiResult.Success -> {
                    saving = false
                    onDone()
                }
                is ApiResult.Failure -> {
                    saving = false
                    // 版本冲突等失败：保留已选内容，不清空草稿
                    error = r.error.describe()
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置我的身份") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                "这些信息只用来判断哪些通知与你有关",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                }
            }

            SelectorRow("学校", school?.name, loading) { picker = PickerField.SCHOOL }
            SelectorRow("学院", college?.name, school != null) { picker = PickerField.COLLEGE }
            SelectorRow("专业", major?.name, college != null) { picker = PickerField.MAJOR }
            SelectorRow("年级", grade?.label, true) { picker = PickerField.GRADE }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "专业大类：${majorGroup ?: "由后端根据专业自动归纳"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { save() },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("保存并进入首页")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("先跳过，稍后再说")
            }
        }
    }

    // ---------------- 选择弹窗 ----------------
    val options: List<Pair<String, String>> = when (picker) {
        PickerField.SCHOOL -> schools.map { it.id to it.name }
        PickerField.COLLEGE -> colleges.map { it.id to it.name }
        PickerField.MAJOR -> majors.map { it.id to it.name }
        PickerField.GRADE -> grades.map { it.value to it.label }
        null -> emptyList()
    }

    val title = when (picker) {
        PickerField.SCHOOL -> "选择学校"
        PickerField.COLLEGE -> "选择学院"
        PickerField.MAJOR -> "选择专业"
        PickerField.GRADE -> "选择年级"
        null -> ""
    }

    if (picker != null) {
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(title) },
            text = {
                if (options.isEmpty()) {
                    Text("暂未支持该组合，请返回上一步重新选择")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(options) { (id, name) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val field = picker
                                        picker = null
                                        when (field) {
                                            PickerField.SCHOOL -> {
                                                val node = schools.firstOrNull { it.id == id }
                                                if (node != null && node.id != school?.id) {
                                                    school = node
                                                    // 只清空正在编辑的下级
                                                    college = null
                                                    major = null
                                                    colleges = emptyList()
                                                    majors = emptyList()
                                                    loadColleges(node.id)
                                                }
                                            }
                                            PickerField.COLLEGE -> {
                                                val node = colleges.firstOrNull { it.id == id }
                                                if (node != null && node.id != college?.id) {
                                                    college = node
                                                    major = null
                                                    majors = emptyList()
                                                    school?.let { loadMajors(it.id, node.id) }
                                                }
                                            }
                                            PickerField.MAJOR -> {
                                                major = majors.firstOrNull { it.id == id }
                                            }
                                            PickerField.GRADE -> {
                                                grade = grades.firstOrNull { it.value == id }
                                            }
                                            null -> Unit
                                        }
                                    }
                                    .padding(vertical = 14.dp),
                            ) {
                                Text(name, style = MaterialTheme.typography.bodyLarge)
                                HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picker = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SelectorRow(
    label: String,
    value: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = value ?: if (enabled) "未选择" else "请先选择上一级",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}
