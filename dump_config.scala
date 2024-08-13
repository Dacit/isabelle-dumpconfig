/*  Author:     Fabian Huch, TU Muenchen

Dumps Isabelle config.
*/

package isabelle



object Dump_Config {
  def dump_config(options: Options, progress: Progress = new Progress()): Unit = {

  }


  /* Isabelle tool wrapper */

  val isabelle_tool = Isabelle_Tool("dump_config", "dumps config", Scala_Project.here,
  { args =>
    var options = Options.init()

    val getopts = Getopts("""
Usage: isabelle dump_config [OPTIONS]

Options are:
  -o OPTION    override Isabelle system OPTION (via NAME=VAL or NAME)

Dumps the config.
""",
      "o:" -> (arg => options = options + arg))

    val more_args = getopts(args)
    if (more_args.nonEmpty) getopts.usage()

    val progress = new Console_Progress()

    dump_config(options, progress = progress)
  })
}

class Dump_Config_Tools extends Isabelle_Scala_Tools(Dump_Config.isabelle_tool)
