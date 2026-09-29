package de.softwareforge.factorio;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import static de.softwareforge.factorio.FactorioLocaleHighlighter.*;

%%
%public
%class FactorioLocaleLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%state HEADER KEY VALUE
%eof{ return;
%eof}

NEWLINE = \r\n|\r|\n
SPACE = [ \t\f]+
PLACEHOLDER = "__"[A-Za-z0-9_-]+"__"
TAG = "["[./]?[A-Za-z][A-Za-z0-9_-]*("="[^\]\r\n]*)?"]"

%%
<YYINITIAL> {
    {SPACE} { return TokenType.WHITE_SPACE; }
    [#;][^\r\n]* { return COMMENT; }
    "[" { yybegin(HEADER); return SECTION; }
    "=" { yybegin(VALUE); return SEPARATOR; }
    [^\r\n=\[ \t\f#;][^\r\n=]* { yybegin(KEY); return PROPERTY; }
}
<HEADER> {
    "]" { yybegin(KEY); return SECTION; }
    [^\]\r\n]+ { return SECTION; }
}
<KEY> {
    "=" { yybegin(VALUE); return SEPARATOR; }
    {SPACE} { return TokenType.WHITE_SPACE; }
    [^=\r\n \t\f]+ { return TEXT; }
}
<VALUE> {
    {PLACEHOLDER} { return PARAMETER; }
    {TAG} { return MARKUP; }
    "\\n" { return ESCAPE; }
    [^_\[\\\r\n]+ { return TEXT; }
    [_\[\\] { return TEXT; }
}
{NEWLINE} { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }
[^] { return TEXT; }
