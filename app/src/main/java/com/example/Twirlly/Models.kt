package com.example.twirlly

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class Comment(
    val user: String, val text: String, val id: Long = 0L, val time: Long = 0L,
    val likes: Int = 0, val liked: Boolean = false
)

fun wordCount(s: String): Int = s.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

data class Video(
    val id: Long,
    val url: String,
    val isImage: Boolean = false,
    val local: Boolean = false,
    val user: String,
    val caption: String,
    val music: String,
    val likes: Int,
    val liked: Boolean = false,
    val saved: Boolean = false,
    val shares: Int = 0,
    val comments: List<Comment> = emptyList(),
    val createdAt: Long = 0L,
    val images: List<String> = emptyList(),
    val audio: String? = null,
    val project: String? = null
)

data class Profile(
    val name: String = "creator",
    val displayName: String = "",
    val bio: String = "Hello! I'm new on Twirlly.",
    val avatar: String? = null,
    val banner: String? = null,
    val isPrivate: Boolean = false
)

const val MAX_OWN_POSTS = 40
const val STORAGE_LIMIT = 5_000_000L

data class AppSettings(
    val email: String = "",
    val notifications: Boolean = true,
    val autoplay: Boolean = true,
    val muted: Boolean = false,
    val theme: Int = 0,
    val accent: Int = 0,
    val onboarded: Boolean = false,
    val premium: Boolean = false
)

data class ActivityEvent(val id: Long, val text: String, val time: Long, val type: Int = 0, val videoId: Long = 0L, val sys: Boolean = true)

private fun Video.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("url", url); put("img", isImage); put("local", local)
    put("user", user); put("cap", caption); put("music", music); put("likes", likes)
    put("liked", liked); put("saved", saved); put("shares", shares); put("t", createdAt)
    put("comments", JSONArray().also { arr ->
        comments.forEach {
            arr.put(JSONObject().put("u", it.user).put("x", it.text).put("i", it.id).put("t", it.time).put("l", it.likes).put("k", it.liked))
        }
    })
    put("images", JSONArray(images)); put("audio", audio ?: ""); put("project", project ?: "")
}

private fun JSONObject.toVideo(): Video {
    val cs = optJSONArray("comments") ?: JSONArray()
    val imgs = optJSONArray("images") ?: JSONArray()
    return Video(
        id = getLong("id"), url = getString("url"), isImage = optBoolean("img"), local = optBoolean("local"),
        user = getString("user"), caption = getString("cap"), music = optString("music"), likes = optInt("likes"),
        liked = optBoolean("liked"), saved = optBoolean("saved"), shares = optInt("shares"),
        comments = (0 until cs.length()).map {
            val o = cs.getJSONObject(it)
            Comment(o.getString("u"), o.getString("x"), o.optLong("i"), o.optLong("t"), o.optInt("l"), o.optBoolean("k"))
        },
        createdAt = optLong("t"),
        images = (0 until imgs.length()).map { imgs.getString(it) },
        audio = optString("audio", "").takeIf { it.isNotBlank() },
        project = optString("project", "").takeIf { it.isNotBlank() }
    )
}

/** All data of the app. Everything is stored on the phone and every screen reads from here, so a change shows up everywhere. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val stateFile = File(ctx.filesDir, "twirlly_state.json")
    private val writer = Executors.newSingleThreadExecutor()

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()
    private val _following = MutableStateFlow<Set<String>>(emptySet())
    val following: StateFlow<Set<String>> = _following.asStateFlow()
    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    private val _events = MutableStateFlow<List<ActivityEvent>>(emptyList())
    val events: StateFlow<List<ActivityEvent>> = _events.asStateFlow()

    init {
        load()
        ensureDemo()
    }

    private fun ensureDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            val demo = _videos.value.filter { !it.local }
            if (demo.isEmpty() || demo.any { !File(it.url).exists() }) {
                val fresh = DemoContent.build(ctx).map { f ->
                    demo.firstOrNull { it.id == f.id }?.let { o ->
                        f.copy(liked = o.liked, saved = o.saved, shares = o.shares, likes = o.likes, comments = o.comments)
                    } ?: f
                }
                _videos.update { cur -> fresh + cur.filter { it.local } }
                persist()
            }
        }
    }

    private fun load() {
        try {
            if (!stateFile.exists()) { addEvent("Welcome to Twirlly"); return }
            val o = JSONObject(stateFile.readText())
            val arr = o.getJSONArray("videos")
            _videos.value = (0 until arr.length()).map { arr.getJSONObject(it).toVideo() }
                .filter { !it.local || File(it.url).exists() || it.project != null }
            val f = o.optJSONArray("following") ?: JSONArray()
            _following.value = (0 until f.length()).map { f.getString(it) }.toSet()
            o.optJSONObject("profile")?.let { p ->
                _profile.value = Profile(
                    name = p.optString("name", "creator"), displayName = p.optString("dn", ""),
                    bio = p.optString("bio", ""),
                    avatar = p.optString("avatar", "").takeIf { it.isNotBlank() },
                    banner = p.optString("banner", "").takeIf { it.isNotBlank() },
                    isPrivate = p.optBoolean("private")
                )
            }
            o.optJSONObject("settings")?.let { s ->
                _settings.value = AppSettings(
                    notifications = s.optBoolean("notif", true), autoplay = s.optBoolean("auto", true),
                    muted = s.optBoolean("muted", false), theme = s.optInt("theme", 0), accent = s.optInt("accent", 0),
                    onboarded = s.optBoolean("onb", false), premium = s.optBoolean("prem", false), email = s.optString("email", "")
                )
            }
            val ev = o.optJSONArray("events") ?: JSONArray()
            _events.value = (0 until ev.length()).map {
                val e = ev.getJSONObject(it)
                ActivityEvent(e.getLong("id"), e.getString("x"), e.getLong("t"), e.optInt("ty"), e.optLong("vid"), e.optBoolean("sys", true))
            }
        } catch (e: Exception) {
            _videos.value = emptyList()
        }
    }

    private fun buildJson(): JSONObject = JSONObject().apply {
        put("videos", JSONArray().also { a -> _videos.value.forEach { a.put(it.toJson()) } })
        put("following", JSONArray(_following.value.toList()))
        val p = _profile.value
        put("profile", JSONObject().put("name", p.name).put("dn", p.displayName).put("bio", p.bio)
            .put("avatar", p.avatar ?: "").put("banner", p.banner ?: "").put("private", p.isPrivate))
        val s = _settings.value
        put("settings", JSONObject().put("notif", s.notifications).put("auto", s.autoplay).put("muted", s.muted)
            .put("theme", s.theme).put("accent", s.accent).put("onb", s.onboarded).put("prem", s.premium).put("email", s.email))
        put("events", JSONArray().also { a ->
            _events.value.forEach { a.put(JSONObject().put("id", it.id).put("x", it.text).put("t", it.time).put("ty", it.type).put("vid", it.videoId).put("sys", it.sys)) }
        })
    }

    fun exportJson(): String = buildJson().toString(2)

    private fun persist() {
        val json = buildJson().toString()
        writer.execute { try { stateFile.writeText(json) } catch (_: Exception) {} }
    }

    private fun addEvent(text: String, type: Int = 0, videoId: Long = 0L, sys: Boolean = true) {
        _events.update { listOf(ActivityEvent(System.nanoTime(), text, System.currentTimeMillis(), type, videoId, sys)) + it.take(79) }
    }

    private fun toast(msg: String) {
        viewModelScope.launch(Dispatchers.Main) { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun mutate(id: Long, f: (Video) -> Video) {
        _videos.update { l -> l.map { if (it.id == id) f(it) else it } }
        persist()
    }

    fun toggleLike(id: Long) {
        val v = _videos.value.firstOrNull { it.id == id } ?: return
        mutate(id) { it.copy(liked = !it.liked, likes = it.likes + if (it.liked) -1 else 1) }
        if (!v.liked) addEvent("You liked @${v.user}'s post", 1, id)
    }

    fun doubleTapLike(id: Long) {
        val v = _videos.value.firstOrNull { it.id == id } ?: return
        if (v.liked) return
        mutate(id) { it.copy(liked = true, likes = it.likes + 1) }
        addEvent("You liked @${v.user}'s post", 1, id)
    }

    fun toggleSave(id: Long) {
        val v = _videos.value.firstOrNull { it.id == id } ?: return
        mutate(id) { it.copy(saved = !it.saved) }
        if (!v.saved) addEvent("You saved @${v.user}'s post", 3, id)
    }

    fun share(id: Long) {
        val v = _videos.value.firstOrNull { it.id == id } ?: return
        mutate(id) { it.copy(shares = it.shares + 1) }
        addEvent("You shared @${v.user}'s post", 4, id)
    }

    fun addComment(id: Long, text: String) {
        if (text.isBlank() || wordCount(text) > 500) return
        val me = _profile.value.name
        val c = Comment(me, text.trim(), System.nanoTime(), System.currentTimeMillis())
        val owner = _videos.value.firstOrNull { it.id == id }?.user ?: ""
        mutate(id) { it.copy(comments = it.comments + c) }
        addEvent("You commented on @$owner's post: " + c.text.take(60), 2, id)
    }

    fun deleteComment(id: Long, index: Int) = mutate(id) { v ->
        if (index in v.comments.indices) v.copy(comments = v.comments.filterIndexed { i, _ -> i != index }) else v
    }

    fun toggleCommentLike(id: Long, index: Int) = mutate(id) { v ->
        if (index !in v.comments.indices) v
        else v.copy(comments = v.comments.mapIndexed { i, c ->
            if (i == index) c.copy(liked = !c.liked, likes = c.likes + if (c.liked) -1 else 1) else c
        })
    }

    fun toggleFollow(user: String) {
        val now = user !in _following.value
        _following.update { if (user in it) it - user else it + user }
        addEvent(if (now) "You followed @$user" else "You unfollowed @$user", 5)
        persist()
    }

    fun postProject(p: Project, caption: String, onDone: () -> Unit) {
        if (p.clips.isEmpty()) return
        val me = _profile.value.name
        val v = Video(
            id = System.currentTimeMillis(), url = p.clips[0].path, isImage = false, local = true, user = me,
            caption = caption.ifBlank { "My new edit" },
            music = p.audios.firstOrNull()?.name?.ifBlank { "Added song" } ?: "Original sound - $me",
            likes = 0, createdAt = System.currentTimeMillis(), project = p.toJson()
        )
        // limited storage: only links to your gallery are kept, and only the newest posts
        _videos.update { cur ->
            val all = listOf(v) + cur
            val keep = all.filter { it.local }.take(MAX_OWN_POSTS).map { it.id }.toSet()
            all.filter { !it.local || it.id in keep }
        }
        addEvent("Your edit was posted", 6, v.id)
        persist()
        if (_settings.value.notifications) Notifier.send(ctx, "Posted on Twirlly", "Your edit is live on your profile.")
        onDone()
    }

    fun deletePost(id: Long) {
        val v = _videos.value.firstOrNull { it.id == id } ?: return
        _videos.update { l -> l.filter { it.id != id } }
        addEvent("Post deleted")
        persist()
    }

    /** Name and username. The username is unique (it cannot match a creator in the app) and changes everywhere at once. */
    fun saveProfile(displayName: String, username: String, bio: String, isPrivate: Boolean): String? {
        val n = username.trim().lowercase()
        if (!Regex("^[a-z0-9_.]{3,24}$").matches(n)) return "Username must be 3 to 24 letters, numbers, _ or ."
        if (DemoContent.creators.any { it.name == n }) return "That username is already taken."
        val old = _profile.value
        _profile.value = old.copy(name = n, displayName = displayName.trim().take(40), bio = bio.trim().take(200), isPrivate = isPrivate)
        if (n != old.name) {
            _videos.update { l ->
                l.map { v ->
                    v.copy(
                        user = if (v.local) n else v.user,
                        music = if (v.local && v.music.startsWith("Original sound - ")) "Original sound - $n" else v.music,
                        comments = v.comments.map { c -> if (c.user == old.name) c.copy(user = n) else c }
                    )
                }
            }
        }
        addEvent("Profile updated")
        persist()
        return null
    }

    fun setImageBytes(bytes: ByteArray, banner: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(ctx.filesDir, "media")
                dir.mkdirs()
                val f = File(dir, (if (banner) "banner_" else "avatar_") + System.currentTimeMillis() + ".jpg")
                f.writeBytes(bytes)
                val old = if (banner) _profile.value.banner else _profile.value.avatar
                if (old != null) File(old).delete()
                _profile.update { if (banner) it.copy(banner = f.absolutePath) else it.copy(avatar = f.absolutePath) }
                addEvent(if (banner) "Banner updated" else "Profile picture updated", 7)
                persist()
            } catch (e: Exception) {
                toast("Could not save the picture")
            }
        }
    }

    fun updateSettings(f: (AppSettings) -> AppSettings) {
        _settings.update(f)
        persist()
    }

    fun setPremium(on: Boolean) {
        _settings.update { it.copy(premium = on) }
        if (on) addEvent("Welcome to Twirlly Premium", 8)
        persist()
    }

    fun completeOnboarding(displayName: String, username: String): String? {
        val err = saveProfile(displayName, username, _profile.value.bio, false)
        if (err != null) return err
        _settings.update { it.copy(onboarded = true) }
        persist()
        return null
    }

    fun mediaBytes(): Long =
        File(ctx.filesDir, "media").walkTopDown().filter { it.isFile }.sumOf { it.length() } + (if (stateFile.exists()) stateFile.length() else 0L)

    fun clearTemp() {
        try { ctx.cacheDir.deleteRecursively() } catch (_: Exception) {}
        ensureDemo()
    }

    fun signIn(email: String) {
        _settings.update { it.copy(email = email.trim()) }
        persist()
    }

    fun signOut() {
        _settings.update { it.copy(email = "", onboarded = false) }
        persist()
    }
}
