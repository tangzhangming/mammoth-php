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
    : visibility? CLASS identifier LBRACE classMember* RBRACE
    ;

classMember
    : fieldDeclaration
    | methodDeclaration
    ;

fieldDeclaration
    : visibility? type? variableDeclarator SEMICOLON
    ;

variableDeclarator
    : VARIABLE (ASSIGN expression)?
    ;

methodDeclaration
    : visibility? STATIC? FUNCTION identifier LPAREN parameters? RPAREN returnType? block
    ;

returnType
    : COLON type
    ;

parameters
    : parameter (COMMA parameter)*
    ;

parameter
    : type? VARIABLE (ASSIGN expression)?
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
    ;

block
    : LBRACE statement* RBRACE
    ;

statement
    : expressionStatement
    | returnStatement
    | localVarDeclaration
    | block
    ;

localVarDeclaration
    : type variableDeclarator SEMICOLON
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
    | MINUS expression                                             #unaryMinusExpr
    | expression op=(MULTIPLY | DIVIDE | MODULO) expression        #multiplicativeExpr
    | expression op=(PLUS | MINUS) expression                      #additiveExpr
    | VARIABLE ASSIGN expression                                   #assignmentExpr
    ;

primary
    : literal
    | closureExpression
    | callExpression
    | VARIABLE
    | LPAREN expression RPAREN
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

// Comments
LINE_COMMENT    : '//' ~[\r\n]* -> skip;
BLOCK_COMMENT   : '/*' .*? '*/' -> skip;

// Whitespace
WS  : [ \t\r\n]+ -> skip;
