##!/bin/zsh
p=$1
id=$2
targetClassSrc=$3

function getdiff () {
git diff -U0 --diff-filter=AMCRD D4J_${p}_${id}_BUGGY_VERSION D4J_${p}_${id}_FIXED_VERSION ${targetClassSrc}\
 | grep -e '^@@'
}

cd /Users/ezaki/Desktop/research/experiment/defects4j/${p}/${p}_${id}_buggy
getdiff