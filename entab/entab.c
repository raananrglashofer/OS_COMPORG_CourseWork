#include <stdio.h>
#include <ctype.h>

int main() {
    int col = 0;
    int c;
    int spaceCount = 0;
    int tabCount = 0;
    int whiteSpace = 0;

    while ((c = getchar()) != EOF) {
        if (c == ' ' && whiteSpace == 0) {
            spaceCount++;
            if(spaceCount == 4){
                tabCount++;
                spaceCount -= 4;
            }
        }
        else if(c == '\t' && whiteSpace == 0){
            tabCount++;
            spaceCount = 0;
        }
        else {
            whiteSpace = 1;
            while (tabCount > 0) {
                putchar('\t');
                tabCount--;
            }
            if (spaceCount > 0) {
                putchar('\t');
                spaceCount = 0;
            }
            col++;
            putchar(c);
            if(c == '\n'){
                whiteSpace = 0;
            }
        }
    }
    return 0;
}

