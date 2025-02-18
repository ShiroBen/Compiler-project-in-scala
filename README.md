This proejct is a Scala-based compiler project for a programming language called "Amy Language", which is a subset of Scala. In this project, we will compile legal Amy programs into WASM codes, which you can run in browser or `nodejs`.

To see the specifications of this language, please refer to: https://lara.epfl.ch/w/_media/cc18/amy-spec.pdf

To use the compiler, please make sure you have `sbt` installed and in the root directory, run it as:
```
sbt run [input-file]
```
By default, if the input program is `p` this command will generate 4 files in the subdirectory in which `p.wasm` is the binary output of the compiler.

To run the binary codes, you can simply run:
```
nodejs [wasm-file]
```
(Caution: Make sure you have nodejs 12 or later version.)
