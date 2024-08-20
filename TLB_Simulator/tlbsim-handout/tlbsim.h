/* 
 * tlbsim.h - Prototypes for TLB simulator helper functions
 */

#ifndef TLBSIM_TOOLS_H
#define TLBSIM_TOOLS_H



/* 
 * printSummary - This function provides a standard way for your TLB
 * simulator * to display its final hit and miss statistics
 */ 
void printSummary(int hits,  /* number of  hits */
				  int misses, /* number of misses */
				  int evictions); /* number of evictions */


#endif /* TLBSIM_TOOLS_H */
