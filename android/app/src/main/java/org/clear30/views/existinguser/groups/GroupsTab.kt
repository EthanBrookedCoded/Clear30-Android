package org.clear30.views.existinguser.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.clear30.data.GroupController
import org.clear30.data.model.Clear30Group
import org.clear30.data.model.Clear30GroupMember
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.GradientActionButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * GroupsTab — ported from the Group views. Shows the user's accountability group
 * (header + member ranking by days checked in) via [GroupController], or a
 * create/join prompt when they aren't in one. The create/join/leave actions
 * fan out to [GroupController.create] / [GroupController.join] /
 * [GroupController.leave], each of which writes through to Supabase and then
 * refreshes the group state.
 */
@Composable
fun GroupsTab(userInfo: UserInfo) {
    val controller = remember { GroupController(userInfo) }
    val group by controller.group.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { controller.refresh() }

    // Deep link → auto-join with a code from clear30://group/<code>. We only
    // fire when the user isn't already in a group; otherwise the link is a
    // no-op and we drop it. Errors surface through the regular `error` field.
    val sub by org.clear30.AppState.pendingSubRoute.collectAsStateWithLifecycle()
    LaunchedEffect(sub) {
        val r = sub as? org.clear30.data.DeepLinkRoute.Group ?: return@LaunchedEffect
        org.clear30.AppState.requestSubRoute(null)
        if (group != null) return@LaunchedEffect
        busy = true
        error = controller.join(r.code)
        busy = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        val g = group
        if (g == null) {
            Heading1("Groups")
            SmallText(
                "Stay accountable with a small group. Create one or join with a code.",
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
            GradientActionButton("plus", Clear30Gradients.clear30, "Create a group") { showCreate = true }
            GradientActionButton("person.3", Clear30Gradients.community, "Join a group") { showJoin = true }
        } else {
            GroupHeader(g, onLeave = { showLeaveConfirm = true })
            Clear30Card(modifier = Modifier.fillMaxWidth()) { GroupCalendar(g, userInfo) }
            g.members.sortedByDescending { it.daysCheckedIn }.forEach { MemberRow(it) }

            // Group notes — short text the user can drop for the group to see.
            // Posts via GroupController.addNote, which refreshes the group on
            // success so the new note appears here without an extra fetch.
            GroupNotesSection(notes = g.notes.orEmpty(), onPost = { msg ->
                scope.launch {
                    busy = true
                    error = controller.addNote(msg)
                    busy = false
                }
            })
        }

        error?.let { msg ->
            SmallText(msg, color = Clear30Colors.red2.copy(alpha = 0.75f))
        }
    }

    if (showCreate) {
        CreateGroupDialog(
            busy = busy,
            onDismiss = { showCreate = false; error = null },
            onSubmit = { name ->
                if (name.isBlank()) return@CreateGroupDialog
                busy = true
                scope.launch {
                    error = controller.create(name.trim())
                    busy = false
                    if (error == null) showCreate = false
                }
            },
        )
    }
    if (showJoin) {
        JoinGroupDialog(
            busy = busy,
            onDismiss = { showJoin = false; error = null },
            onSubmit = { code ->
                if (code.isBlank()) return@JoinGroupDialog
                busy = true
                scope.launch {
                    error = controller.join(code.trim())
                    busy = false
                    if (error == null) showJoin = false
                }
            },
        )
    }
    if (showLeaveConfirm) {
        LeaveGroupDialog(
            busy = busy,
            onDismiss = { showLeaveConfirm = false },
            onConfirm = {
                busy = true
                scope.launch {
                    error = controller.leave()
                    busy = false
                    if (error == null) showLeaveConfirm = false
                }
            },
        )
    }
}

@Composable
private fun GroupNotesSection(notes: List<org.clear30.data.model.Clear30GroupNote>, onPost: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Heading2("Notes")
        if (notes.isEmpty()) {
            SmallText("No notes yet — drop a quick check-in for the group.",
                color = Clear30Colors.text.copy(alpha = 0.5f))
        } else {
            notes.sortedByDescending { it.timestamp }.forEach { note ->
                Clear30Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        // Author label — uses the same UserDirectory cache as
                        // CommunityTab so a group note posted by a member
                        // shows their real name once the lookup lands.
                        val entry = remember(note.fromMemberID) {
                            org.clear30.data.UserDirectory.lookup(note.fromMemberID)
                        }
                        TinyText(entry.displayName, color = Clear30Colors.text.copy(alpha = 0.5f))
                        SmallText(note.message)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Share a note…") })
            DefaultButton("Post", gradient = Clear30Gradients.clear30) {
                if (draft.isNotBlank()) { onPost(draft); draft = "" }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: Clear30Group, onLeave: () -> Unit) {
    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = group.gradient) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Heading2(group.name ?: "Your Group", color = Color.White)
                TinyText("${group.members.size} members · ${group.daysSober} days clear together", color = Color.White)
                // Group id is the join "code" — surface it so members can invite.
                TinyText("Code: ${group.id}", color = Color.White.copy(alpha = 0.5f))
            }
            Spacer(Modifier.weight(1f))
            DefaultButton("Leave", gradient = Clear30Gradients.red) { onLeave() }
        }
    }
}

@Composable
private fun MemberRow(member: Clear30GroupMember) {
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            SmallText(member.emoji)
            SmallText(member.name)
            Spacer(Modifier.weight(1f))
            TinyText("${member.daysCheckedIn} days", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun CreateGroupDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                Text("Pick a name — your friends will see it when they join.")
                OutlinedTextField(name, { name = it }, label = { Text("Group name") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name) },
                enabled = !busy && name.isNotBlank(),
            ) { Text(if (busy) "Creating…" else "Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun JoinGroupDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                Text("Paste the group code from a friend.")
                OutlinedTextField(code, { code = it }, label = { Text("Group code") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(code) },
                enabled = !busy && code.isNotBlank(),
            ) { Text(if (busy) "Joining…" else "Join") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun LeaveGroupDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave this group?") },
        text = { Text("You'll lose access to the group calendar, notes, and member updates. You can always join again with the code.") },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(if (busy) "Leaving…" else "Leave")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
