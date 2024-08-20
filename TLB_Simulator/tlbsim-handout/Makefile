#
# Student makefile for TLB Simulator
# Note: requires a 64-bit x86-64 system 
#
CC = gcc
CFLAGS = -g -Wall -Werror -std=c99 -m64


csim: csim.c tlbsim.c tlbsim.h
	$(CC) $(CFLAGS) -o csim csim.c tlbsim.c -lm 

#
# Clean the src dirctory
#
clean:
	rm -rf *.o
	rm -f *.tar
	rm -f csim
	rm -f test-trans tracegen
	rm -f trace.all trace.f*
	rm -f .csim_results .marker
