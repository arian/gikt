package com.github.arian.gikt

import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.repository.Repository
import com.github.arian.gikt.database.Commit as DbCommit

class Revision(private val repo: Repository, private val expression: String) {

    private val query by lazy { parse(expression) }

    fun resolve(): ObjectId =
        when (val res = query?.resolve(repo)) {
            is Res.Commit -> res.commit.oid
            is Res.Errors -> throw InvalidObject("Not a valid object name: '$expression'", res.errors)
            else -> throw InvalidObject("Not a valid object name: '$expression'")
        }

    class InvalidObject(msg: String, val errors: List<HintedError> = emptyList()) : Exception(msg)
    class HintedError(msg: String, val hints: List<String> = emptyList()) : Exception(msg)

    internal sealed class Rev {
        data class Ref(val name: String) : Rev()
        data class Parent(val rev: Rev) : Rev()
        data class Ancestor(val rev: Rev, val n: Int) : Rev()
    }

    internal sealed class Res {
        object Null : Res()
        data class Commit(val commit: DbCommit) : Res()
        data class Errors(val errors: List<HintedError>) : Res()
    }

    companion object {

        private val INVALID_NAME = Regex(
            """
              ^\.
            | \/\.
            | \.\.
            | \/$
            | \.lock$
            | @\{
            | [\x00-\x20*:?\[\\^~\x7f]
            """.trimIndent(),
            RegexOption.COMMENTS
        )

        const val HEAD = "HEAD"

        private val PARENT = Regex("^(.+)\\^$")
        private val ANCESTOR = Regex("^(.+)~(\\d+)$")
        private val REF_ALIASES = mapOf(
            "@" to HEAD
        )

        fun validRef(revision: String) = !INVALID_NAME.containsMatchIn(revision)

        private fun parseParent(revision: String): Rev? =
            PARENT.matchEntire(revision)
                ?.destructured
                ?.let { (ref) -> parse(ref) }
                ?.let { Rev.Parent(it) }

        private fun parseAncestor(revision: String): Rev? =
            ANCESTOR.matchEntire(revision)
                ?.destructured
                ?.let { (ref, num) ->
                    parse(ref)?.let { rev ->
                        num.toIntOrNull()?.let { n -> Rev.Ancestor(rev, n) }
                    }
                }

        private fun parseRef(revision: String): Rev? =
            revision
                .takeIf { validRef(it) }
                ?.let { Rev.Ref(REF_ALIASES.getOrDefault(it, it)) }

        internal fun parse(revision: String): Rev? =
            parseParent(revision)
                ?: parseAncestor(revision)
                ?: parseRef(revision)

        internal fun Rev.resolve(repo: Repository): Res =
            when (this) {
                is Rev.Parent -> rev.resolve(repo).parent(repo)
                is Rev.Ancestor -> (0 until n).fold(rev.resolve(repo)) { it, _ -> it.parent(repo) }
                is Rev.Ref -> readRef(name, repo)
            }

        private fun Res.parent(repo: Repository): Res =
            when (this) {
                is Res.Commit -> commitParent(commit.oid, repo)
                else -> this
            }

        private fun ObjectId?.toRes(repo: Repository) = when (this) {
            null -> Res.Null
            else -> when (val obj = repo.loadObject(this)) {
                is DbCommit -> Res.Commit(obj)
                else -> Res.Errors(listOf(HintedError("object ${obj.oid} is a ${obj.type}, not a commit")))
            }
        }

        private fun commitParent(oid: ObjectId, repo: Repository): Res =
            when (val result = oid.toRes(repo)) {
                is Res.Commit -> result.commit.parent.toRes(repo)
                else -> result
            }

        private fun readRef(name: String, repo: Repository): Res =
            repo.refs.readRef(name)?.toRes(repo)
                ?: matchObjectId(name, repo)

        private fun matchObjectId(oid: String, repo: Repository): Res {
            val matches = repo.database.prefixMatch(oid)
            return when {
                matches.isEmpty() -> Res.Null
                matches.size == 1 -> matches.first().toRes(repo)
                else -> Res.Errors(ambiguousSha1Hints(oid, matches, repo))
            }
        }

        private fun ambiguousSha1Hints(oid: String, oids: List<ObjectId>, repo: Repository): List<HintedError> {
            val objects = oids.sortedBy { it.hex }.map {

                val obj = repo.loadObject(it)
                val info = " ${it.short} ${obj.type}"

                when (obj) {
                    is DbCommit -> "$info ${obj.author.shortDate} - ${obj.title}"
                    else -> info
                }
            }

            val message = "short SHA1 $oid is ambiguous"
            val hints = listOf("The candidates are:") + objects
            return listOf(HintedError(message, hints))
        }
    }
}
