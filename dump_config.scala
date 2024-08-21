/*  Author:     Fabian Huch, TU Muenchen

Dumps Isabelle config.
*/

package isabelle


import isabelle.setup.Environment

import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

import java.io.FileOutputStream


object Dump_Config {

  def dump_export(
    options: Options,
    output_dir: Option[Path],
    sessions: List[String],
    progress: Progress = new Progress(),
    name: String = "exportTrace"
  ): Unit = {
    val store = Store(options)

    val compress_cache = Compress.Cache.make()
    val xml_cache = XML.Cache.make(compress_cache)

    def read_traces(db: SQL.Database, session_name: String): List[Bytes] =
      db.execute_query_statement(
        Export.private_data.Base.table.select(
          List(Export.private_data.Base.body),
          sql = SQL.where_and(
            Export.private_data.Base.session_name.equal(session_name),
            Export.private_data.Base.name.name + " LIKE '" + name + "%'")),
          make_result = List.from[Bytes],
          get = { res =>
            res.bytes(Export.private_data.Base.body)
          })

    val space =
      using(store.open_server()) { server =>
        def read(session_name: String): Space = {
          Exn.capture {
            using(Export.open_session_context0(store, session_name, server = server)) {
              session_context =>
                progress.echo("Processing " + session_name + " ...")
                val traces =
                  for {
                    db <- session_context.session_db().toList
                    bytes <- read_traces(db, session_name)
                  } yield bytes

                if (traces.isEmpty) {
                  progress.echo("No traces for " + session_name)
                  Space.zero
                }
                else {
                  output_dir.foreach { dir =>
                    val file = dir + Path.basic(session_name)
                    using(new FileOutputStream(file.file)) { stream =>
                      traces.foreach(_.write_stream(stream))
                    }
                  }
                  val res = traces.map(bytes => Space.bytes(bytes.size)).foldLeft(Space.zero)(_ + _)
                  progress.echo("Read " + session_name + " with " + res.print)
                  res
                }
            }
          } match {
            case Exn.Res(res) => res
            case Exn.Exn(e) =>
              progress.echo_error_message("Could not read: " + e)
              Space.zero
          }
        }
        Par_List.map(read, sessions).foldLeft(Space.zero)(_ + _)
      }
    progress.echo("Total of " + space.print)
  }

  def dump_config(
    options: Options,
    output_dir: Path,
    selection: Sessions.Selection = Sessions.Selection.empty,
    afp_root: Option[Path] = None,
    dirs: List[Path] = Nil,
    select_dirs: List[Path] = Nil,
    progress: Progress = new Progress()
  ): Unit = {
    val store = Store(options)

    val full_structure =
      Sessions.load_structure(options, dirs = AFP.main_dirs(afp_root) ::: dirs,
        select_dirs = select_dirs)
    val structure = full_structure.selection(selection)
    val deps = Sessions.deps(structure, progress = progress).check_errors

    val sessions =
      structure.build_topological_order.map { session_name =>
        progress.echo("Processing " + session_name + " ...")
        val session = store.get_session(session_name)

        JSON.Object(
          "name" -> session_name,
          "heap_file" -> session.heap.map(_.canonical.implode).getOrElse(""),
          "log_db_file" -> session.log_db.map(_.canonical.implode).getOrElse(""),
          "base_sessions" -> full_structure.build_graph.imm_preds(session_name).toList,
          "session_imports" -> full_structure.imports_graph.imm_preds(session_name).toList,
          "thys" -> deps(session_name).known_theories.view.mapValues(entry => JSON.Object(
            "dir" -> entry.name.path.canonical.implode,
          )).toMap)
      }

    val json = JSON.Object("sessions" -> sessions)

    File.write(output_dir + Path.basic("dump_config").json, JSON.Format(json))
  }


  /* Isabelle tool wrapper */

  val isabelle_tool1 = Isabelle_Tool("dump_export", "dumps export from db to file",
    Scala_Project.here,
    { args =>
      var afp_root: Option[Path] = None
      val base_sessions = new mutable.ListBuffer[String]
      val select_dirs = new mutable.ListBuffer[Path]
      var output_dir: Option[Path] = None
      var requirements = false
      val exclude_session_groups = new mutable.ListBuffer[String]
      var all_sessions = false
      val dirs = new mutable.ListBuffer[Path]
      val session_groups = new mutable.ListBuffer[String]
      var options = Options.init()
      val exclude_sessions = new mutable.ListBuffer[String]

      val getopts = Getopts("""
  Usage: isabelle dump_export [OPTIONS] [SESSIONS...]

    Options are:
      -A ROOT      include AFP with given root directory (":" for \"\"\" + AFP.BASE.implode + \"\"\")
      -B NAME      include session NAME and all descendants
      -D DIR       include session directory and select its sessions
      -O DIR       output dir
      -R           refer to requirements of selected sessions
      -X NAME      exclude sessions from group NAME and all descendants
      -a           select all sessions
      -d DIR       include session directory
      -g NAME      select session group NAME
      -o OPTION    override Isabelle system OPTION (via NAME=VAL or NAME)
      -x NAME      exclude session NAME and all descendants

    Dumps export form db to file.
  """,
        "A:" -> (arg => afp_root = Some(if (arg == ":") AFP.BASE else Path.explode(arg))),
        "B:" -> (arg => base_sessions += arg),
        "D:" -> (arg => select_dirs += Path.explode(arg)),
        "O:" -> (arg => output_dir = Some(Path.explode(arg))),
        "R" -> (_ => requirements = true),
        "X:" -> (arg => exclude_session_groups += arg),
        "a" -> (_ => all_sessions = true),
        "d:" -> (arg => dirs += Path.explode(arg)),
        "g:" -> (arg => session_groups += arg),
        "o:" -> (arg => options = options + arg),
        "x:" -> (arg => exclude_sessions += arg))

      val sessions = getopts(args)

      val progress = new Console_Progress(verbose = true)

      val full_structure =
        Sessions.load_structure(options, dirs = AFP.main_dirs(afp_root) ::: dirs.toList, 
          select_dirs = select_dirs.toList)
      val structure = full_structure.selection(
        Sessions.Selection(
          requirements = requirements,
          all_sessions = all_sessions,
          base_sessions = base_sessions.toList,
          exclude_session_groups = exclude_session_groups.toList,
          exclude_sessions = exclude_sessions.toList,
          session_groups = session_groups.toList,
          sessions = sessions))

      val sessions1 = structure.build_topological_order

      dump_export(options, output_dir, sessions1, progress)
    })

  val isabelle_tool = Isabelle_Tool("dump_config", "dumps config", Scala_Project.here,
    { args =>
      var afp_root: Option[Path] = None
      val base_sessions = new mutable.ListBuffer[String]
      val select_dirs = new mutable.ListBuffer[Path]
      var output_dir: Path = Path.current
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
      -O DIR       output dir
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
        "O:" -> (arg => output_dir = Path.explode(arg)),
        "R" -> (_ => requirements = true),
        "X:" -> (arg => exclude_session_groups += arg),
        "a" -> (_ => all_sessions = true),
        "d:" -> (arg => dirs += Path.explode(arg)),
        "g:" -> (arg => session_groups += arg),
        "o:" -> (arg => options = options + arg),
        "x:" -> (arg => exclude_sessions += arg))

      val sessions = getopts(args)

      val progress = new Console_Progress(verbose = true)

      dump_config(options,
        output_dir,
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

class Dump_Config_Tools extends Isabelle_Scala_Tools(
  Dump_Config.isabelle_tool, Dump_Config.isabelle_tool1)
