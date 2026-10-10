package de.softwareforge.factorio;

import java.util.*;

/** A deliberately small Lua suffix parser: names, members and literal table indices, never calls. */
final class FactorioReplExpression {
    private static final Set<String> KEYWORDS=Set.of("and","break","do","else","elseif","end","false","for","function",
            "goto","if","in","local","nil","not","or","repeat","return","then","true","until","while");
    record Key(String value,boolean numeric) {}
    enum Kind { NAME, MEMBER, BRACKET, QUOTED }
    record Query(List<Key> path,String prefix,Kind kind,char quote) {
        boolean accepts(String name,boolean numeric) {
            return switch(kind) {
                case NAME, MEMBER -> !numeric && identifier(name) && name.startsWith(prefix);
                case QUOTED -> !numeric && escape(name,quote).startsWith(prefix);
                case BRACKET -> (numeric ? name : "\""+escape(name,'"')+"\"").startsWith(prefix);
            };
        }
        String insertion(String name,boolean numeric) {
            return switch(kind) {
                case NAME, MEMBER -> name;
                case QUOTED -> escape(name,quote);
                case BRACKET -> numeric ? name : "\""+escape(name,'"')+"\"";
            };
        }
        String closing() { return kind==Kind.QUOTED ? quote+"]" : kind==Kind.BRACKET ? "]" : ""; }
    }
    private record Token(String text,int start,boolean string,boolean complete) {}

    static Query parse(String text,int offset) {
        if (offset<0 || offset>text.length() || offset>16384) return null;
        text=text.substring(0,offset);
        var tokens=new ArrayList<Token>();
        for (int i=0;i<text.length();) {
            char c=text.charAt(i);
            if (Character.isWhitespace(c)) { i++;continue; }
            int start=i;
            if (text.startsWith("--",i)) {
                int end=text.indexOf('\n',i);
                if (end<0 || text.startsWith("--[",i)) return null;
                i=end+1;continue;
            }
            // Long strings/comments and computed expressions are intentionally outside this grammar.
            if (c=='[' && i+1<text.length() && (text.charAt(i+1)=='[' || text.charAt(i+1)=='=')) return null;
            if (c=='\'' || c=='"') {
                i++;
                while (i<text.length() && text.charAt(i)!=c) {
                    if (text.charAt(i)=='\\') i++;
                    i++;
                }
                boolean complete=i<text.length();
                if (complete) i++;
                tokens.add(new Token(text.substring(start,Math.min(i,text.length())),start,true,complete));
            } else {
                if (Character.isLetterOrDigit(c) || c=='_') {
                    do { i++; } while (i<text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i)=='_'));
                } else i++;
                tokens.add(new Token(text.substring(start,i),start,false,true));
            }
        }
        int end=tokens.size()-1;
        String prefix="";Kind kind=Kind.NAME;char quote='"';
        if (end>=0) {
            var last=tokens.get(end);
            if (last.string() && !last.complete()) {
                if (end==0 || !tokens.get(end-1).text().equals("[")) return null;
                prefix=last.text().substring(1);quote=last.text().charAt(0);kind=Kind.QUOTED;end-=2;
            } else {
                if (last.start()+last.text().length()==offset && last.text().matches("[A-Za-z_0-9]+")) {
                    prefix=last.text();end--;
                }
                if (end>=0 && Set.of(".",":","[").contains(tokens.get(end).text())) {
                    kind=tokens.get(end).text().equals("[") ? Kind.BRACKET : Kind.MEMBER;end--;
                } else {
                    if (prefix.isEmpty() && end>=0 && (tokens.get(end).string() || Set.of(")","]").contains(tokens.get(end).text()))) return null;
                    if (!prefix.isEmpty() && !identifier(prefix)) return null;
                    return new Query(List.of(),prefix,Kind.NAME,quote);
                }
            }
        } else return new Query(List.of(),"",Kind.NAME,quote);
        var path=new ArrayList<Key>();
        boolean rooted=false;
        while (end>=0) {
            if (path.size()>=16) return null;
            var token=tokens.get(end);
            if (token.text().equals("]")) {
                if (end<2) return null;
                var value=tokens.get(end-1);
                String decoded=value.string() && value.complete() ? unquote(value.text()) : null;
                if (decoded!=null) path.add(new Key(decoded,false));
                else if (value.text().matches("\\d+")) path.add(new Key(value.text(),true));
                else return null;
                end-=2;
                if (decoded==null && end>=0 && tokens.get(end).text().equals("-")) { var key=path.removeLast();path.add(new Key("-"+key.value(),true));end--; }
                if (end<0 || !tokens.get(end).text().equals("[")) return null;
                end--;
            } else if (!token.string() && identifier(token.text())) {
                path.add(new Key(token.text(),false));end--;
                if (end<0 || !tokens.get(end).text().equals(".")) { rooted=true;break; }
                end--;
            } else return null;
        }
        if (!rooted || path.isEmpty() || (end>=0 && Set.of(".",":", "]", ")").contains(tokens.get(end).text()))) return null;
        // The root must be a name (not the trailing portion of a call or literal index).
        if (end>=0 && tokens.get(end).text().equals("[")) return null;
        Collections.reverse(path);
        return new Query(List.copyOf(path),prefix,kind,quote);
    }

    static boolean identifier(String text) { return text.matches("[A-Za-z_][A-Za-z_0-9]*") && !KEYWORDS.contains(text); }
    static String escape(String text,char quote) {
        return text.replace("\\","\\\\").replace(String.valueOf(quote),"\\"+quote)
                .replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
    }
    static String unquote(String text) {
        if (text.length()<2 || (text.charAt(0)!='\'' && text.charAt(0)!='"') || text.charAt(text.length()-1)!=text.charAt(0)) return null;
        var out=new StringBuilder();
        for (int i=1;i<text.length()-1;i++) {
            char c=text.charAt(i);
            if (c=='\\') {
                if (++i>=text.length()-1) return null;
                c=text.charAt(i);
                c=switch(c) { case 'n' -> '\n';case 'r' -> '\r';case 't' -> '\t';case '\\','\'', '"' -> c;default -> 0; };
                if (c==0) return null;
            }
            out.append(c);
        }
        return out.toString();
    }
}
