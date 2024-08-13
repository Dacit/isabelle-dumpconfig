/*  Author:     Fabian Huch, TU Muenchen

Dumps Isabelle config.
*/

package isabelle


import scala.collection.mutable


object Dump_Config {
  def dump_config(
    options: Options,
    selection: Sessions.Selection = Sessions.Selection.empty,
    afp_root: Option[Path] = None,
    dirs: List[Path] = Nil,
    select_dirs: List[Path] = Nil,
    progress: Progress = new Progress()
  ): Unit = {
    val store = Store(options)

    val full_sessions =
      Sessions.load_structure(options, dirs = AFP.main_dirs(afp_root) ::: dirs,
        select_dirs = select_dirs)
    val full_sessions_selection = full_sessions.imports_selection(selection)


  }


  /* Isabelle tool wrapper */

  val isabelle_tool = Isabelle_Tool("dump_config", "dumps config", Scala_Project.here,
  { args =>
    var afp_root: Option[Path] = None
    val base_sessions = new mutable.ListBuffer[String]
    val select_dirs = new mutable.ListBuffer[Path]
    var requirements = false
    val exclude_session_groups = new mutable.ListBuffer[String]
    var all_sessions = false
    val dirs = new mutable.ListBuffer[Path]
    val session_groups = new mutable.ListBuffer[String]
    var options = Options.init()
    val exclude_sessions = new mutable.ListBuffer[String]

    val getopts = Getopts("""
Usage: isabelle dump_config [OPTIONS] [SESSIONS...]

  Options are:
    -A ROOT      include AFP with given root directory (":" for \"\"\" + AFP.BASE.implode + \"\"\")
    -B NAME      include session NAME and all descendants
    -D DIR       include session directory and select its sessions
    -R           refer to requirements of selected sessions
    -X NAME      exclude sessions from group NAME and all descendants
    -a           select all sessions
    -d DIR       include session directory
    -g NAME      select session group NAME
    -o OPTION    override Isabelle system OPTION (via NAME=VAL or NAME)
    -x NAME      exclude session NAME and all descendants

  Dumps the config.
""",
      "A:" -> (arg => afp_root = Some(if (arg == ":") AFP.BASE else Path.explode(arg))),
      "B:" -> (arg => base_sessions += arg),
      "D:" -> (arg => select_dirs += Path.explode(arg)),
      "R" -> (_ => requirements = true),
      "X:" -> (arg => exclude_session_groups += arg),
      "a" -> (_ => all_sessions = true),
      "d:" -> (arg => dirs += Path.explode(arg)),
      "g:" -> (arg => session_groups += arg),
      "o:" -> (arg => options = options + arg),
      "x:" -> (arg => exclude_sessions += arg))

    val sessions = getopts(args)

    val progress = new Console_Progress()

    dump_config(options,
      selection = Sessions.Selection(
        requirements = requirements,
        all_sessions = all_sessions,
        base_sessions = base_sessions.toList,
        exclude_session_groups = exclude_session_groups.toList,
        exclude_sessions = exclude_sessions.toList,
        session_groups = session_groups.toList,
        sessions = sessions),
      progress = progress,
      afp_root = afp_root,
      dirs = dirs.toList,
      select_dirs = select_dirs.toList)
  })
}

class Dump_Config_Tools extends Isabelle_Scala_Tools(Dump_Config.isabelle_tool)
