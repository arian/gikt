package com.github.arian.gikt

import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.repository.Repository

class Revision(private val repo: Repository, private val expression: String) {

    private val query by lazy { parse(expression) }

    fun resolve(): ObjectId =
        query?.let { resolve(it, repo) }
            ?: throw InvalidObject("Not a valid object name: '$expression'")

    class InvalidObject(msg: String) : Exception(msg)

    sealed class Rev {
        data class Ref(val name: String) : Rev()
        data class Parent(val rev: Rev) : Rev()
        data class Ancestor(val rev: Rev, val n: Int) : Rev()
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

        private val PARENT = Regex("^(.+)\\^$")
        private val ANCESTOR = Regex("^(.+)~(\\d+)$")
        private val REF_ALIASES = mapOf(
            "@" to "HEAD"
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

        fun parse(revision: String): Rev? =
            parseParent(revision)
                ?: parseAncestor(revision)
                ?: parseRef(revision)

        fun resolve(revision: Rev, repo: Repository): ObjectId? =
            when (revision) {
                is Rev.Parent -> resolve(revision.rev, repo)?.let {
                    commitParent(it, repo)
                }
                is Rev.Ancestor -> (0 until revision.n)
                    .fold(resolve(revision.rev, repo)) { oid: ObjectId?, _ ->
                        oid?.let { commitParent(it, repo) }
                    }
                is Rev.Ref -> readRef(revision.name, repo)
            }

        private fun commitParent(oid: ObjectId, repo: Repository): ObjectId? =
            when (val obj = repo.loadObject(oid)) {
                is Commit -> obj.parent
                else -> null
            }

        private fun readRef(name: String, repo: Repository): ObjectId? =
            repo.refs.readRef(name)
    }
}
