#include "tlbsim.h"
#include <getopt.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

static unsigned int totalHits = 0;
static unsigned int totalMisses = 0;
static unsigned int totalEvictions = 0;

typedef struct {
    unsigned int isValid;
    unsigned long tagValue;
    unsigned int leastRecentlyUsed;
} MemoryBlock;

// Function prototype declaration for manageMissesAndEvictions
void manageMissesAndEvictions(MemoryBlock ** simulationCache, unsigned long indexSet, unsigned long tagIdent, unsigned int blocksPerSet, int emptySpot);

void processMemoryAccess(MemoryBlock ** simulationCache, unsigned long indexSet, unsigned long tagIdent, unsigned int setCount, unsigned int blocksPerSet){
    int found = 0;
    int emptySpot = -1;
    MemoryBlock * currentSet = simulationCache[indexSet];
    for(int i = 0; i < blocksPerSet; i++){
        MemoryBlock currentBlock = currentSet[i];
        if(currentBlock.isValid == 1){
            if(currentBlock.tagValue == tagIdent){
                found = 1;
                totalHits++;
                simulationCache[indexSet][i].leastRecentlyUsed = 0;
                break;
            }
            else{
                simulationCache[indexSet][i].leastRecentlyUsed += 1;
            }
        }
        else{
            emptySpot = i;
        }
    }
    if(!found){
        manageMissesAndEvictions(simulationCache, indexSet, tagIdent, blocksPerSet, emptySpot);
    }
}

void manageMissesAndEvictions(MemoryBlock ** simulationCache, unsigned long indexSet, unsigned long tagIdent, unsigned int blocksPerSet, int emptySpot){
    if(emptySpot != -1){
        MemoryBlock newBlock = {1, tagIdent, 0};
        simulationCache[indexSet][emptySpot] = newBlock;
        totalMisses++;
    }
    else{
        int highestLRU = 0, toEvict = -1;
        for(int i = 0; i < blocksPerSet; i++){
            if(simulationCache[indexSet][i].isValid && simulationCache[indexSet][i].leastRecentlyUsed >= highestLRU){
                highestLRU = simulationCache[indexSet][i].leastRecentlyUsed;
                toEvict = i;
            }
        }
        MemoryBlock replacementBlock = {1, tagIdent, 0};
        simulationCache[indexSet][toEvict] = replacementBlock;
        totalMisses++;
        totalEvictions++;
    }
}

MemoryBlock ** initializeCache(unsigned int setCount, unsigned int blocksPerSet){
    MemoryBlock ** simulationCache = (MemoryBlock**)malloc(setCount * sizeof(MemoryBlock*));
    for(unsigned int i = 0; i < setCount; i++){
        simulationCache[i] = (MemoryBlock*)malloc(blocksPerSet * sizeof(MemoryBlock));
    }
    return simulationCache;
}

void releaseCache(MemoryBlock ** simulationCache, unsigned int setCount){
    for(unsigned int i = 0; i < setCount; i++){
        free(simulationCache[i]);
    }
    free(simulationCache);
}

void parseInputAndSimulate(char * filePath, unsigned int blockSize, unsigned int setBits, unsigned int setCount, unsigned int blocksPerSet, MemoryBlock ** simulationCache){
    FILE * inputFile = fopen(filePath, "r");
    char operation;
    unsigned long address;
    int size;
    while(fscanf(inputFile, " %c %lx,%d", &operation, &address, &size) != EOF){
        if(operation == 'I') continue;
        if(operation == 'M') totalHits++;

        unsigned long tag = address >> (setBits + blockSize);
        unsigned long setIndex = (address >> blockSize) & (setCount - 1);
        processMemoryAccess(simulationCache, setIndex, tag, setCount, blocksPerSet);
    }

    fclose(inputFile);
}

int main(int argc, char **argv) {
    unsigned int cacheSetBits = 0;
    unsigned int cacheBlocksPerSet = 0;
    unsigned int blockOffsetBits = 0;
    char traceFilePath[1000] = "";

    int opt = 0;
    while ((opt = getopt(argc, argv, "s:E:b:t:")) != -1) {
        switch (opt) {
            case 's':
                cacheSetBits = atoi(optarg);
                break;
            case 'E':
                cacheBlocksPerSet = atoi(optarg);
                break;
            case 'b':
                blockOffsetBits = atoi(optarg);
                break;
            case 't':
                strcpy(traceFilePath, optarg);
                break;
            default:
                break;
        }
    }

    unsigned int numOfSets = 1 << cacheSetBits;
    MemoryBlock **simulationCache = initializeCache(numOfSets, cacheBlocksPerSet);
    parseInputAndSimulate(traceFilePath, blockOffsetBits, cacheSetBits, numOfSets, cacheBlocksPerSet, simulationCache);
    releaseCache(simulationCache, numOfSets);
    printSummary(totalHits, totalMisses, totalEvictions);

    return 0;
}


