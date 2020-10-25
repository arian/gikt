import com.github.arian.gikt.commands.util.PrintDiff
import com.github.arian.gikt.commands.util.PrintDiff.Companion.NULL_PATH
import com.github.arian.gikt.commands.util.PrintDiff.Target
    private val cached: Boolean by cli.option(ArgType.Boolean).default(false)
    private val staged: Boolean by cli.option(ArgType.Boolean).default(false)
    private val printDiffOptions = PrintDiff.Options(cli, default = true)
    private val printDiff = PrintDiff(fmt = ::fmt)
    private fun printDiff(a: Target, b: Target) {
        println(printDiff.diff(a, b))
        if (!printDiffOptions.patch) {
            return
        }
                is Status.WorkspaceChange.Deleted -> printDiff(fromIndex(change.entry), fromNothing(NULL_PATH))
        if (!printDiffOptions.patch) {
            return
        }
    private fun fromNothing(path: String): Target =
        Target.fromNothing(path)
    private fun fromHead(entry: TreeEntry): Target =
        Target.fromHead(entry, repository.loadObject(entry.oid))