# Isabelle Dump Config
Tool to dump all Isabelle info. Result should look something like this:

```json
{
  "env": {
      "NAME": "value"
  },
  "sessions": [{
      "name": "...",
      "db_file": "...",
      "base_session": "...",
      "session_imports": "...",
      "thys": [{
          "name": "HOL.Groups",
          "dir": "/home/...bla/Groups.thy",
          "symbolic_dir": "$SOMETHINGELSE/thy/Groups.thy"
      }]
  }]
}
```

## Installation
Simply add the component to Isabelle:
```isabelle components -u <THIS_REPO_DIR>```

## Usage
Run `isabelle dump_config -?` to see usage instructions.
