#!/usr//bin/python
#
# driver.py - The driver tests the correctness of the TLB 
#     simulator.  It uses ./test-csim to check the correctness of the
#     simulator.
#
import subprocess;
import re;
import os;
import sys;
import optparse;

#
# computeMissScore - compute the score depending on the number of
# cache misses
#
def computeMissScore(miss, lower, upper, full_score):
    if miss <= lower:
        return full_score
    if miss >= upper: 
        return 0

    score = (miss - lower) * 1.0 
    range = (upper- lower) * 1.0
    return round((1 - score / range) * full_score, 1)

#
# main - Main function
#
def main():

    # Configure maxscores here
    maxscore= {};
    maxscore['csim'] = 27
    

    # Parse the command line arguments 
    p = optparse.OptionParser()
    p.add_option("-A", action="store_true", dest="autograde", 
                 help="emit autoresult string for Autolab");
    opts, args = p.parse_args()
    autograde = opts.autograde

    # Check the correctness of the cache simulator
    print ("Testing TLB simulator")
    print ("Running ./test-csim")
    p = subprocess.Popen("./test-csim", shell=True, stdout=subprocess.PIPE)
    stdout_data = p.communicate()[0]
    stdout_data = stdout_data.decode('utf-8')

    # Emit the output from test-csim
    stdout_data = re.split('\n', stdout_data)
    for line in stdout_data:
        if re.match("TEST_CSIM_RESULTS", line):
            resultsim = re.findall(r'(\d+)', line)
        else:
            print ("%s" % (line))

    
    # Compute the scores for each step
    csim_cscore  = list(map(int, resultsim[0:1]))
    
    total_score = csim_cscore[0]

    # Summarize the results
    print ("\nTLB Simulator summary:")
    print ("%-22s%8s%10s%12s" % ("", "Points", "Max pts", "Misses"))
    print ("%-22s%8.1f%10d" % ("Csim correctness", csim_cscore[0], maxscore['csim']))

    
    print ("%22s%8.1f%10d" % ("Total points", total_score, maxscore['csim'] ))
    
    
# execute main only if called as a script
if __name__ == "__main__":
    main()

