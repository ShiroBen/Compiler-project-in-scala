error id: file://<WORKSPACE>/src/amyc/extension-examples/messycode.scala:[7..8) in Input.VirtualFile("file://<WORKSPACE>/src/amyc/extension-examples/messycode.scala", "object {def main() : Unit = { println(new A().foo(−41)); }
}
class A {
def foo(i : Int) : Int = {
var j : Int;
if(i < 0) { j = 0 − i; } else { j = i; }
return j + 1;
}
}")
file://<WORKSPACE>/src/amyc/extension-examples/messycode.scala
file://<WORKSPACE>/src/amyc/extension-examples/messycode.scala:1: error: expected identifier; obtained lbrace
object {def main() : Unit = { println(new A().foo(−41)); }
       ^
#### Short summary: 

expected identifier; obtained lbrace