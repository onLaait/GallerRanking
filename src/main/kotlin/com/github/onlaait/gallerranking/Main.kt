package com.github.onlaait.gallerranking

import be.zvz.kotlininside.KotlinInside
import be.zvz.kotlininside.api.article.ArticleList
import be.zvz.kotlininside.api.async.article.AsyncArticleRead
import be.zvz.kotlininside.api.async.comment.AsyncCommentRead
import be.zvz.kotlininside.api.comment.CommentRead
import be.zvz.kotlininside.api.type.comment.DCConComment
import be.zvz.kotlininside.http.DefaultHttpClient
import be.zvz.kotlininside.session.user.Anonymous
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.commons.text.StringEscapeUtils.escapeHtml4
import java.text.DecimalFormat
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.math.max
import kotlin.math.min

val users = mutableMapOf<String, UserData>()

fun main() {
    Thread.setDefaultUncaughtExceptionHandler(DefaultExceptionHandler)

    val nCoroutines = 5

    println("클라이언트 생성 중")
    KotlinInside.createInstance(Anonymous("ㅇㅇ", "1234"), DefaultHttpClient())

    var gall: String
    var gallInfo: ArticleList.GallInfo
    while (true) {
        print("갤러리 ID: ")
        gall = readln().trim()
        println("갤러리 정보 불러오는 중")
        val articleList = ArticleList(gall)
        articleList.request()
        gallInfo = articleList.getGallInfo()
        if (gallInfo.title.isBlank()) {
            println("갤러리를 불러올 수 없음")
            continue
        }
        break
    }

    val gallType =
        when {
            gallInfo.isMinor -> "마이너 "
            gallInfo.isMini -> "미니 "
            else -> ""
        }
    println("${gallInfo.title} ${gallType}갤러리")

    var inputId1: Int
    var inputId2: Int
    while (true) {
        try {
            print("시작 글 ID: ")
            inputId1 = readln().trim().toInt()
            print("끝 글 ID: ")
            inputId2 = readln().trim().toInt()
            break
        } catch (_: Exception) {
        }
    }
    val startId = min(inputId1, inputId2)
    val endId = max(inputId1, inputId2)

    val ids = (startId..endId).toMutableList()

    var count = 0
    var success = 0
    var deleted = 0
    runBlocking {
        repeat(nCoroutines) {
            launch {
                while (ids.isNotEmpty()) {
                    val articleId = ids.first()
                    ids.remove(articleId)
                    count++
                    val articleRead = AsyncArticleRead(gall, articleId)
                    while (true) {
                        try {
                            articleRead.requestAsync().await()
                            break
                        } catch (_: Exception) {
                            println("$articleId 재시도")
                        }
                    }
                    val viewInfo = articleRead.getViewInfoAsync().await()
                    if (viewInfo.identifier == 0) {
                        deleted++
                        ids -= articleId
                        println("$articleId 삭제됨")
                        continue
                    }
                    val viewMain = articleRead.getViewMainAsync().await()
                    if (viewInfo.userId.isNotBlank()) { // 글쓴이
                        users.getOrPut(viewInfo.userId) { UserData() }.run {
                            name = viewInfo.name
                            articles++
                            upvotes += viewMain.upvoteMember
                            downvotes += viewMain.downvote
                            if (viewMain.upvoteMember > 0 || viewMain.downvote > 0) {
                                articleList +=
                                    Article(
                                        viewInfo.identifier,
                                        viewInfo.subject,
                                        if (viewMain.upvoteMember > 0) viewMain.upvoteMember * 500 + (viewMain.upvote - viewMain.upvoteMember) * 50 + viewInfo.totalComment else 0,
                                        if (viewMain.downvote > 0) viewMain.downvote * 100 + viewInfo.totalComment else 0
                                    )
                            }

                        }
                    }
                    if (viewInfo.totalComment > 0) { // 댓글
                        var page = 0
                        while (true) {
                            page++
                            val commentRead = AsyncCommentRead(gall, articleId, page)
                            var commentReadRes: CommentRead.ReadResult
                            while (true) {
                                try {
                                    commentReadRes = commentRead.getAsync().await()
                                    break
                                } catch (_: Exception) {
                                    println("$articleId 댓글$page 재시도")
                                }
                            }
                            for (comment in commentReadRes.commentList) {
                                if (comment.userId.isBlank()) continue
                                users.getOrPut(comment.userId) { UserData() }.let {
                                    it.name = comment.name
                                    it.comments++
                                    if (comment.content is DCConComment) it.commentsCon++
                                }
                            }
                            if (commentReadRes.rePage == commentReadRes.totalPage) break
                        }
                    }
                    success++
                    ids -= articleId
                    println(articleId)
                }
            }
        }
    }

    val max = endId - startId + 1
    val error = max - success - deleted
    println("$max 중 $count 계산, $success 성공, $deleted 삭제됨, $error${if (error != 0) ids else ""} 오류")

    println("데이터 정리 중...")

    val result = mutableListOf<String>()
    result += "<p><span style=\"font-size:18pt;\"><b><i>${gallInfo.title} 갤러리 갤창랭킹</i></b><br></span><span style=\"font-size:36pt;\"><br></span></p>"
    result += "<p><span style=\"font-size:10pt;\">총 게시글 수: $success <span style=\"color: rgb(166, 166, 166);\">($startId-$endId)</span><br></span><span style=\"font-size:18pt;\"><br></span></p>"

    val articleUrl =
        when {
            gallInfo.isMinor -> "https://gall.dcinside.com/m/$gall/%s"
            gallInfo.isMini -> "https://gall.dcinside.com/mini/$gall/%s"
            else -> "https://gall.dcinside.com/$gall/%s"
        }


    val decimalFormat = DecimalFormat("0.0")

    repeat(4) { r ->
        var i = 0
        var top = 0
        var lastVal = 0
        val rank = mutableListOf<List<Any>>()
        when (r) {
            0 -> { // 게시글 랭킹
                for (it in users.filterValues { it.articles >= 1 }.entries.sortedByDescending { it.value.articles }.take(100)) {
                    i++
                    if (it.value.articles != lastVal) top = i
                    rank += listOf(top, formatUser(it.value.name, it.key), it.value.articles)
                    lastVal = it.value.articles
                }
                result += makeHtmlTable("게시글 랭킹", listOf("게시글 수"), rank)
                result += "<span style=\"font-size:36pt;\"><br><br></span>"
            }

            1 -> { // 댓글 랭킹
                for (it in users.filterValues { it.comments >= 1 }.entries.sortedByDescending { it.value.comments }.take(100)) {
                    i++
                    if (it.value.comments != lastVal) top = i
                    rank += listOf(
                        top,
                        formatUser(it.value.name, it.key),
                        it.value.comments,
                        "<span style=\"font-size:8pt;\">${
                            decimalFormat.format(it.value.commentsCon.toDouble() / it.value.comments * 100)
                        }</span>"
                    )
                    lastVal = it.value.comments
                }
                result += makeHtmlTable(
                    "댓글 랭킹",
                    listOf("댓글 수", "<span style=\"font-size:8pt;\">디시콘 비율(%)</span>"),
                    rank
                )
                result += "<span style=\"font-size:36pt;\"><br><br></span>"
            }

            2 -> { // 개추 랭킹
                for (it in users.filterValues { it.upvotes >= 10 }.entries.sortedByDescending { it.value.upvotes }.take(50)) {
                    i++
                    if (it.value.upvotes != lastVal) top = i
                    rank += listOf(
                        top,
                        formatUser(it.value.name, it.key),
                        it.value.upvotes,
                        it.value.articleList.maxBy { it.up }.run {
                            "<a href=\"${String.format(articleUrl, id)}\" style=\"color:#006DD7;\"><span style=\"font-size:8pt;\">$subject</span></a>"
                        }
                    )
                    lastVal = it.value.upvotes
                }
                result += makeHtmlTable(
                    "개추 랭킹",
                    listOf("고닉추 수", "<span style=\"font-size:8pt;\">BEST</span>"),
                    rank
                )
                result += "<span style=\"font-size:36pt;\"><br><br></span>"
            }

            3 -> { // 비추 랭킹
                for (it in users.filterValues { it.downvotes >= 10 }.entries.sortedByDescending { it.value.downvotes }.take(50)) {
                    i++
                    if (it.value.downvotes != lastVal) top = i
                    rank += listOf(
                        top,
                        formatUser(it.value.name, it.key),
                        it.value.downvotes,
                        it.value.articleList.maxBy { it.down }.run {
                            "<a href=\"${String.format(articleUrl, id)}\" style=\"color:#006DD7;\"><span style=\"font-size:8pt;\">$subject</span></a>"
                        }
                    )
                    lastVal = it.value.downvotes
                }
                result += makeHtmlTable(
                    "비추 랭킹",
                    listOf("비추 수", "<span style=\"font-size:8pt;\">WORST</span>"),
                    rank
                )
                result += "<span style=\"font-size:36pt;\"><br><br></span>"
            }
        }
    }
    Path("result_${gall}_${startId}-${endId}.txt").bufferedWriter().use { w ->
        result.forEach {
            w.write(it)
            w.newLine()
        }
    }
    println("완료")
}

fun formatUser(name: String, id: String) = "$name<span style=\"color:rgb(166,166,166);\">($id)</span>"

fun makeHtmlTable(title: String, extraIndex: List<String>, contents: MutableList<List<Any>>): String {
    val index = listOf("순위", "닉네임(아이디)") + extraIndex
    for (i in 0..contents.lastIndex) { // 1~3등 색 입히기
        if (contents[i][0] !in 1..3) break
        val list = contents[i].toMutableList()
        list[0] = when (list[0]) {
            1 -> "<span style=\"color:rgb(213,161,30);\"><b>1</b></span>"
            2 -> "<span style=\"color:rgb(163,163,163);\"><b>2</b></span>"
            3 -> "<span style=\"color:rgb(205,127,50);\"><b>3</b></span>"
            else -> null
        }!!
        contents[i] = list
    }
    return "<table width=\"100%\" style=\"border-collapse:collapse;\" border=\"1\"><tbody>" +
            (
                listOf("<td colspan=\"${index.size}\" style=\"height:46px;\"><b><span style=\"font-size:18pt;\">${escapeHtml4(title)}</span></b></td>") +
                (listOf(index.map { "<b>$it</b>" }) + contents).map { it.joinToString("") { e -> "<td>$e</td>" } }
            ).joinToString("") { "<tr align=\"center\">$it</tr>" } +
            "</tbody></table>"
}

data class UserData(
    var name: String = "",
    var articles: Int = 0,
    var comments: Int = 0,
    var commentsCon: Int = 0,
    var upvotes: Int = 0,
    var downvotes: Int = 0,
    val articleList: MutableList<Article> = mutableListOf()
)

data class Article(val id: Int, val subject: String, val up: Int, val down: Int)