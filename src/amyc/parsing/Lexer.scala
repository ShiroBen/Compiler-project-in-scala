package amyc
package parsing

import utils._
import java.io.File

import silex._

import amyc.utils.Position

// The lexer for Amy.
object Lexer extends Pipeline[List[File], Iterator[Token]]
                with Lexers {

  /** Tiny Scallion-lexer reference:
    * ==============================
    * Scallion's lexer essentially allows you to define a list of regular expressions
    * in their order of priority. To tokenize a given input stream of characters, each
    * individual regular expression is applied in turn. If a given expression matches, it
    * is used to produce a token of maximal length. Whenever a regular expression does not
    * match, the expression of next-highest priority is tried.
    * The result is a stream of tokens.
    *
    * Regular expressions `r` can be built using the following operators:
    *   - `word("abc")`  matches the sequence "abc" exactly
    *   - `r1 | r2`      matches either expression `r1` or expression `r2`
    *   - `r1 ~ r2`      matches `r1` followed by `r2`
    *   - `oneOf("xy")`  matches either "x" or "y"
    *                    (i.e., it is a shorthand of `word` and `|` for single characters)
    *   - `elem(c)`      matches character `c`
    *   - `elem(f)`      matches any character for which the boolean predicate `f` holds 
    *   - `opt(r)`       matches `r` or nothing at all
    *   - `many(r)`      matches any number of repetitions of `r` (including none at all)
    *   - `many1(r)`     matches any non-zero number of repetitions of `r`
    *  
    * To define the token that should be output for a given expression, one can use
    * the `|>` combinator with an expression on the left-hand side and a function
    * producing the token on the right. The function is given the sequence of matched
    * characters and the source-position range as arguments.
    * 
    * For instance,
    *
    *   `elem(_.isDigit) ~ word("kg") |> {
    *     (cs, range) => WeightLiteralToken(cs.mkString).setPos(range._1)) }`
    *
    * will match a single digit followed by the characters "kg" and turn them into a
    * "WeightLiteralToken" whose value will be the full string matched (e.g. "1kg").
    */


  // Type of characters consumed.
  type Character = Char

  // Type of positions.
  type Position = SourcePosition

  // Type of tokens produced.
  type Token = amyc.parsing.Token

  import Token._

  val lexer = Lexer(
    // Keywords
    word("abstract") | word("case") | word("class") |
    word("def") | word("else") | word("extends") |
    word("if") | word("match") | word("object") |
    word("val") | word("error") | word("_")
      |> { (cs, range) => KeywordToken(cs.mkString).setPos(range._1) },

    // TODO: Primitive type names
    word("Int") | word("String") | word("Boolean") | word("Unit")
      |> {(cs, range) => PrimTypeToken(cs.mkString).setPos(range._1)},

    // TODO: Boolean literals
    word("true") |> {(cs, range) => BoolLitToken(true).setPos(range._1)},
    word("false") |> {(cs, range) => BoolLitToken(false).setPos(range._1)},

    // TODO: Operators
    // NOTE: You can use `oneOf("abc")` as a shortcut for `word("a") | word("b") | word("c")`
    word("==") | word("++") | word("||") | word("&&") | word("<=") | oneOf("+-*/%<!")
      |> {(cs,range) => OperatorToken(cs.mkString).setPos(range._1)},

    // TODO: Identifiers
    elem(_.toString.matches("""[a-zA-Z]""")) ~ many(elem(_.toString.matches("""[a-zA-Z0-9_]""")))
      |> {(cs, range) => IdentifierToken(cs.mkString).setPos(range._1)},

    // TODO: Integer literals
    // NOTE: Make sure to handle invalid (e.g. overflowing) integer values safely by
    //       emitting an ErrorToken instead.
    many1(elem(_.isDigit))
      |> {(cs,range) => 
      try {
        IntLitToken(cs.toString.toInt).setPos(range._1)
      } catch {
        case e: Exception =>
          ErrorToken("Integer overflow detected.").setPos(range._1)
      } 
    },

    // TODO: String literals
    elem('\"') ~ many(elem(c => (c != '\n' && c != '\"'))) ~ elem('\"') |> {(cs,range) => StringLitToken(cs.mkString).setPos(range._1)},

    // TODO: Delimiters and whitespace
    word("=>") | oneOf(".,:;()[]{}=") |> {(cs, range) => DelimiterToken(cs.mkString).setPos(range._1)},

    many1(elem(' ') | elem('\n') | elem('\t')) |> {(cs,range) => SpaceToken()},

    // Single line comments
    word("//") ~ many(elem(_ != '\n'))
      |> { cs => CommentToken(cs.mkString("")) },

    // TODO: Multiline comments
    // NOTE: Amy does not support nested multi-line comments (e.g. `/* foo /* bar */ */`).
    //       Make sure that unclosed multi-line comments result in an ErrorToken.
    word("/*") ~ many(elem(_ != '*')) ~ many1(elem('*')) ~ many(elem(c => (c != '*' && c != '/')) ~ many(elem(_ != '*')) ~ many1(elem('*'))) ~ elem('/')
      |> { (cs,pos) => CommentToken(cs.mkString("")) },

    word("/*") |> { (cs, range) => ErrorToken("Unclosed comment").setPos(range._1) }

  ) onError {
    // We also emit ErrorTokens for Scallion-handled errors.
    (cs, range) => ErrorToken(cs.mkString).setPos(range._1)
  } onEnd {
    // Once all the input has been consumed, we emit one EOFToken.
    pos => EOFToken().setPos(pos)
  }

  override def run(ctx: amyc.utils.Context)(files: List[File]): Iterator[Token] = {
    var it = Seq[Token]().iterator

    for (file <- files) {
      val source = Source.fromFile(file.toString, SourcePositioner(file))
      it ++= lexer.spawn(source).filter {
        token =>
          // TODO: Remove all whitespace and comment tokens
          token match {
            case CommentToken(_) => false
            case SpaceToken() => false
            case _ => true
          }
      }.map {
        case token@ErrorToken(error) => ctx.reporter.fatal("Unknown token at " + token.position + ": " + error)
        case token => token
      }
    }
    it
  }
}

/** Extracts all tokens from input and displays them */
object DisplayTokens extends Pipeline[Iterator[Token], Unit] {
  override def run(ctx: Context)(tokens: Iterator[Token]): Unit = {
    tokens.foreach(println(_))
  }
}