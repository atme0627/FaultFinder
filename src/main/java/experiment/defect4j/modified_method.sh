##!/bin/zsh
p=Math
id=$1
targetClassSrc=$1

function getdiff () {
git diff -U0 --diff-filter=AMCRD D4J_${p}_${id}_BUGGY_VERSION D4J_${p}_${id}_FIXED_VERSION ${targetClassSrc}\
 | grep -e '^@@'
}

function getdiffsummary () {
git diff -U5 --diff-filter=AMCRD D4J_${p}_${id}_BUGGY_VERSION D4J_${p}_${id}_FIXED_VERSION
}

cd /Users/ezaki/Desktop/research/experiment/defects4j/${p}/${p}_${id}_buggy
getdiffsummary