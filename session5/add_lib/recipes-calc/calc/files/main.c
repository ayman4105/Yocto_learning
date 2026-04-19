#include "mymath.h"

#include<stdio.h>

int main(){

    int a = 5;
    int b = 10;
    int c = add(a, b);

    printf("The sum of %d and %d is %d\n", a, b, c);


    int d = sub(a, b);
    printf("The difference of %d and %d is %d\n", a, b, d);

    return 0;

}