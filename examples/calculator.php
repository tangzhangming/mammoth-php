<?php
class Calculator {

    public static function main() :void
    {
        int64 $a = 10;
        int64 $b = 20;
        int64 $sum = $a + $b;
        int64 $diff = $a - $b;
        int64 $prod = $a * $b;
        int64 $quot = $b / $a;

        println("Calculator Demo");
        print("a = ");
        println($a);
        print("b = ");
        println($b);
        print("a + b = ");
        println($sum);
        print("a - b = ");
        println($diff);
        print("a * b = ");
        println($prod);
        print("b / a = ");
        println($quot);
    }

}
