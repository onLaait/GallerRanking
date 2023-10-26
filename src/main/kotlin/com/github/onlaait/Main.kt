package com.github.onlaait

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
import java.io.File
import kotlin.math.max
import kotlin.math.min

val users = mutableMapOf<String, UserData>()

fun main() {
    Thread.setDefaultUncaughtExceptionHandler(DefaultExceptionHandler)

    val coroutineCount = 50

    println("클라이언트 생성 중")
    KotlinInside.createInstance(
        Anonymous("ㅇㅇ", "1234"),
        DefaultHttpClient()
    )

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

    var gallType = ""
    if (gallInfo.isMinor) gallType = "마이너 "
    if (gallInfo.isMini) gallType = "미니 "
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
        } catch (e: Exception) {
        }
    }
    val startId = min(inputId1, inputId2)
    val endId = max(inputId1, inputId2)

    val ids = (startId..endId).toMutableList()

    var count = 0
    var success = 0
    var deleted = 0
    runBlocking {
        repeat(coroutineCount) {
            launch {
                while (ids.isNotEmpty()) {
                    val articleId = ids.first()
                    ids.remove(articleId)
                    count++
                    val articleRead = AsyncArticleRead(gall, articleId)
                    while (true) {
                        try {
                            articleRead.requestAsync().await()
                        } catch (e: Exception) {
                            println("$articleId 재시도")
                            continue
                        }
                        break
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
                        users.putIfAbsent(viewInfo.userId, UserData())
                        users[viewInfo.userId]!!.run {
                            name = viewInfo.name
                            article++
                            upVote += viewMain.upvoteMember
                            downVote += viewMain.downvote
                            if (viewMain.upvoteMember > 0 || viewMain.downvote > 0) {
                                articles += Article(
                                    viewInfo.identifier,
                                    viewInfo.subject,
                                    if (viewMain.upvoteMember > 0) viewMain.upvoteMember * 1000000 + viewMain.upvote * 1000 + viewInfo.totalComment else 0,
                                    if (viewMain.downvote > 0) viewMain.downvote * 1000 + viewInfo.totalComment else 0
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
                                } catch (e: Exception) {
                                    println("$articleId 댓글$page 재시도")
                                    continue
                                }
                                break
                            }
                            for (comment in commentReadRes.commentList) {
                                if (comment.userId.isBlank()) continue
                                users.putIfAbsent(comment.userId, UserData())
                                users[comment.userId]!!.let {
                                    it.name = comment.name
                                    it.comment++
                                    if (comment.content is DCConComment) it.commentDCCon++
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

    val articleUrl = if (gallInfo.isMinor) {
        "https://gall.dcinside.com/m/$gall/%s"
    } else if (gallInfo.isMini) {
        "https://gall.dcinside.com/mini/$gall/%s"
    } else {
        "https://gall.dcinside.com/$gall/%s"
    }

    repeat(4) { r ->
        var i = 0
        var top = 0
        var lastVal = 0
        val rank = mutableListOf<List<Any>>()
        when (r) {
            0 -> {
                for (it in users.filterValues { it.article >= 1 }.entries.sortedByDescending { it.value.article }) {
                    i++
                    if (it.value.article != lastVal) top = i
                    rank += listOf(top, formatUser(it.value.name, it.key), it.value.article)
                    if (i == 100) break
                    lastVal = it.value.article
                }
                result += makeHtmlTable("게시글 랭킹", listOf("게시글 수"), rank)
                result += "<span style=\"font-size:36pt;\"><br><br></span>"
            }

            1 -> {
                for (it in users.filterValues { it.comment >= 1 }.entries.sortedByDescending { it.value.comment }) {
                    i++
                    if (it.value.comment != lastVal) top = i
                    rank += listOf(
                        top,
                        formatUser(it.value.name, it.key),
                        it.value.comment,
                        "<span style=\"font-size:8pt;\">${
                            String.format(
                                " % .1f",
                                it.value.commentDCCon.toDouble() / it.value.comment * 100
                            )
                        }</span>"
                    )
                    if (i == 100) break
                    lastVal = it.value.comment
                }
                result += makeHtmlTable(
                    "댓글 랭킹",
                    listOf("댓글 수", "<span style=\"font-size:8pt;\">디시콘 비율(%)</span>"),
                    rank
                )
                result += "<span style=\"font-size:36pt;\"><br><br></span>"
            }

            2 -> {
                for (it in users.filterValues { it.upVote >= 5 }.entries.sortedByDescending { it.value.upVote }) {
                    i++
                    if (it.value.upVote != lastVal) top = i
                    rank += listOf(
                        top,
                        formatUser(it.value.name, it.key),
                        it.value.upVote,
                        it.value.articles.maxBy { article -> article.up }.run {
                            "<a href=\"${String.format(articleUrl, id)}\" style=\"color:#006DD7;\"><span style=\"font-size:8pt;\">$subject</span></a>"
                        }
                    )
                    if (i == 50) break
                    lastVal = it.value.upVote
                }
                result += makeHtmlTable(
                    "개추 랭킹",
                    listOf("고닉추 수", "<span style=\"font-size:8pt;\">BEST</span>"),
                    rank
                )
                result += "<span style=\"font-size:36pt;\"><br><br></span>"
            }

            3 -> {
                for (it in users.filterValues { it.downVote >= 5 }.entries.sortedByDescending { it.value.downVote }) {
                    i++
                    if (it.value.downVote != lastVal) top = i
                    rank += listOf(
                        top,
                        formatUser(it.value.name, it.key),
                        it.value.downVote,
                        it.value.articles.maxBy { article -> article.down }.run {
                            "<a href=\"${String.format(articleUrl, id)}\" style=\"color:#006DD7;\"><span style=\"font-size:8pt;\">$subject</span></a>"
                        }
                    )
                    if (i == 50) break
                    lastVal = it.value.downVote
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
    File("result_${gall}_${startId}-${endId}.txt").bufferedWriter().use { bw ->
        for ((i, str) in result.withIndex()) {
            bw.write(str)
            if (i == result.lastIndex) break
            bw.newLine()
        }
    }
    println("완료")
}

fun formatUser(name: String, id: String) = "$name<span style=\"color:rgb(166,166,166);\">($id)</span>"

fun makeHtmlTable(title: String, index: List<String>, contents: MutableList<List<Any>>): String {
    val fullIndex = listOf("순위", "닉네임(아이디)") + index
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
            (listOf("<td colspan=\"${fullIndex.size}\" style=\"height:46px;\"><b><span style=\"font-size:18pt;\">$title</span></b></td>") +
                    (listOf(fullIndex.map { "<b>$it</b>" }) + contents).map { it.joinToString("") { e -> "<td>$e</td>" } }
                    ).joinToString("") { "<tr align=\"center\">$it</tr>" } +
            "</tbody></table>"
}

data class UserData(
    var name: String = "",
    var article: Int = 0,
    var comment: Int = 0,
    var commentDCCon: Int = 0,
    var upVote: Int = 0,
    var downVote: Int = 0,
    val articles: MutableList<Article> = mutableListOf()
)

data class Article(val id: Int, val subject: String, val up: Int, val down: Int)