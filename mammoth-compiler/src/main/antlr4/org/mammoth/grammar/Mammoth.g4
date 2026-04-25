grammar Mammoth;

// ============================================================================
// Parser rules
// ============================================================================

program
    : phpTag? packageDeclaration? importDeclaration* classDeclaration+ EOF
    ;

phpTag
    : PHP_OPEN
    ;

packageDeclaration
    : (PACKAGE | NAMESPACE) qualifiedName SEMICOLON
    ;

importDeclaration
    : (IMPORT | USE) qualifiedName SEMICOLON
    ;

classDeclaration
    : annotationUsage* visibility? ANNOTATION CLASS identifier annotationBody
    | annotationUsage* visibility? CLASS identifier LBRACE classMember* RBRACE
    ;

annotationBody
    : LBRACE annotationMember* RBRACE
    ;

annotationMember
    : visibility? type VARIABLE (ASSIGN expression)? SEMICOLON
    ;

classMember
    : fieldDeclaration
    | methodDeclaration
    ;

fieldDeclaration
    : annotationUsage* visibility? type? variableDeclarator SEMICOLON
    ;

variableDeclarator
    : VARIABLE (ASSIGN expression)?
    ;

methodDeclaration
    : annotationUsage* visibility? STATIC? FUNCTION identifier LPAREN parameters? RPAREN returnType? block
    ;

returnType
    : COLON type
    ;

parameters
    : parameter (COMMA parameter)*
    ;

parameter
    : annotationUsage* type? VARIABLE (ASSIGN expression)?
    ;

type
    : nullableType
    | primitiveType
    ;

nullableType
    : QUESTION primitiveType
    ;

primitiveType
    : typeName=STRING
    | typeName=BOOLEAN
    | typeName=INT8
    | typeName=INT16
    | typeName=INT32
    | typeName=INT64
    | typeName=FLOAT32
    | typeName=FLOAT64
    | typeName=BYTE
    | typeName=INT
    | typeName=FLOAT
    | typeName=VOID
    | typeName=NOTHING
    ;

block
    : LBRACE statement* RBRACE
    ;

statement
    : expressionStatement
    | returnStatement
    | localVarDeclaration
    | tryStatement
    | throwStatement
    | ifStatement
    | whileStatement
    | doWhileStatement
    | forStatement
    | forEachStatement
    | breakStatement
    | continueStatement
    | block
    ;

localVarDeclaration
    : type variableDeclarator SEMICOLON
    ;

tryStatement
    : TRY tryBlock=block catchClause* finallyBlock=finallyClause?
    ;

catchClause
    : CATCH LPAREN qualifiedName VARIABLE RPAREN block
    ;

finallyClause
    : FINALLY block
    ;

throwStatement
    : THROW expression SEMICOLON
    ;

// ---- Annotation rules ----

annotationUsage
    : AT (annotationTarget COLON)? qualifiedName (LPAREN annotationArgs? RPAREN)?
    ;

annotationTarget
    : FIELD_TARGET | GET_TARGET | SET_TARGET
    ;

annotationArgs
    : annotationArg (COMMA annotationArg)*
    ;

annotationArg
    : (IDENTIFIER ASSIGN)? expression
    ;

// ---- Control flow ----

ifStatement
    : IF LPAREN expression RPAREN statement (ELSE statement)?
    ;

whileStatement
    : WHILE LPAREN expression RPAREN statement
    ;

doWhileStatement
    : DO statement WHILE LPAREN expression RPAREN SEMICOLON
    ;

forStatement
    : FOR LPAREN forInit? SEMICOLON expression? SEMICOLON forUpdate? RPAREN statement
    ;

forInit
    : type variableDeclarator
    | expression (COMMA expression)*
    ;

forUpdate
    : expression (COMMA expression)*
    ;

forEachStatement
    : FOREACH LPAREN expression AS VARIABLE (ARROW VARIABLE)? RPAREN statement
    ;

breakStatement
    : BREAK SEMICOLON
    ;

continueStatement
    : CONTINUE SEMICOLON
    ;

expressionStatement
    : expression SEMICOLON
    ;

returnStatement
    : RETURN expression? SEMICOLON
    ;

expression
    : primary                                                      #primaryExpr
    | LPAREN type RPAREN expression                                #castExpr
    | (MINUS | NOT) expression                                     #unaryExpr
    | expression op=(MULTIPLY | DIVIDE | MODULO) expression        #multiplicativeExpr
    | expression op=(PLUS | MINUS) expression                      #additiveExpr
    | expression op=(LT | GT | LTE | GTE) expression               #comparisonExpr
    | expression op=(EQ | NEQ) expression                          #equalityExpr
    | expression AND expression                                    #andExpr
    | expression OR expression                                     #orExpr
    | VARIABLE ASSIGN expression                                   #assignmentExpr
    ;

primary
    : literal
    | closureExpression
    | newExpression
    | callExpression
    | VARIABLE
    | IDENTIFIER
    | LPAREN expression RPAREN
    ;

newExpression
    : NEW qualifiedName LPAREN arguments? RPAREN
    ;

callExpression
    : (identifier | builtinPrint | VARIABLE) LPAREN arguments? RPAREN
    ;

builtinPrint
    : PRINT | PRINTF | PRINTLN
    ;

closureExpression
    : FUNCTION LPAREN parameters? RPAREN captureClause? returnType? block
    ;

captureClause
    : USE LPAREN captureList RPAREN
    ;

captureList
    : captureItem (COMMA captureItem)*
    ;

captureItem
    : REF? VARIABLE
    ;

arguments
    : expression (COMMA expression)*
    ;

literal
    : STRING_LITERAL
    | INTEGER_LITERAL
    | FLOAT_LITERAL
    | BOOLEAN_LITERAL
    | NULL
    ;

identifier
    : IDENTIFIER
    ;

qualifiedName
    : IDENTIFIER (DOT IDENTIFIER)*
    ;

visibility
    : PUBLIC
    | PROTECTED
    | PRIVATE
    ;

// ============================================================================
// Lexer rules
// ============================================================================

// PHP tags (skipped)
PHP_OPEN    : '<?php' -> skip;
PHP_CLOSE   : '?>' -> skip;

// Keywords
PACKAGE     : 'package';
NAMESPACE   : 'namespace';
IMPORT      : 'import';
USE         : 'use';
CLASS       : 'class';
PUBLIC      : 'public';
PROTECTED   : 'protected';
PRIVATE     : 'private';
STATIC      : 'static';
FUNCTION    : 'function';
RETURN      : 'return';
NULL        : 'null';
TRY         : 'try';
CATCH       : 'catch';
FINALLY     : 'finally';
THROW       : 'throw';
NEW         : 'new';
ANNOTATION  : 'annotation';
FIELD_TARGET: 'field';
GET_TARGET  : 'get';
SET_TARGET  : 'set';
IF          : 'if';
ELSE        : 'else';
WHILE       : 'while';
DO          : 'do';
FOR         : 'for';
FOREACH     : 'foreach';
AS          : 'as';
BREAK       : 'break';
CONTINUE    : 'continue';

// Builtin functions
PRINT       : 'print';
PRINTF      : 'printf';
PRINTLN     : 'println';

// Type keywords
STRING      : 'string';
BOOLEAN     : 'boolean';
INT8        : 'int8';
INT16       : 'int16';
INT32       : 'int32';
INT64       : 'int64';
FLOAT32     : 'float32';
FLOAT64     : 'float64';
BYTE        : 'byte';
INT         : 'int';
FLOAT       : 'float';
VOID        : 'void';
NOTHING     : 'nothing';

// Boolean literals
BOOLEAN_LITERAL : 'true' | 'false';

// Variable: must start with $
VARIABLE    : '$' [a-zA-Z_] [a-zA-Z_0-9]*;

// Identifier (class name, function name, package name)
IDENTIFIER  : [a-zA-Z_] [a-zA-Z_0-9]*;

// Literals
STRING_LITERAL  : '"' (~["\r\n] | '\\"')* '"';
INTEGER_LITERAL : [0-9]+
                | '0' [xX] [0-9a-fA-F]+
                | '0' [bB] [01]+
                ;
FLOAT_LITERAL   : [0-9]+ '.' [0-9]+ ([eE] [+-]? [0-9]+)?
                | [0-9]+ [eE] [+-]? [0-9]+
                ;

// Operators / Punctuation
PLUS        : '+';
MINUS       : '-';
MULTIPLY    : '*';
DIVIDE      : '/';
MODULO      : '%';
ASSIGN      : '=';
REF         : '&';
LPAREN      : '(';
RPAREN      : ')';
LBRACE      : '{';
RBRACE      : '}';
SEMICOLON   : ';';
COLON       : ':';
COMMA       : ',';
DOT         : '.';
QUESTION    : '?';
AT          : '@';
EQ          : '==';
NEQ         : '!=';
LTE         : '<=';
GTE         : '>=';
LT          : '<';
GT          : '>';
AND         : '&&';
OR          : '||';
NOT         : '!';
ARROW       : '=>';

// Comments
LINE_COMMENT    : '//' ~[\r\n]* -> skip;
BLOCK_COMMENT   : '/*' .*? '*/' -> skip;

// Whitespace
WS  : [ \t\r\n]+ -> skip;
