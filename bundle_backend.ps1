# This script aggregates all Java source files into a format easy to share
$outputFile = "backend_bundle.txt"
"" > $outputFile
$files = Get-ChildItem -Path "src" -Filter *.java -Recurse
foreach ($file in $files) {
    "--- START OF FILE: $($file.FullName) ---" >> $outputFile
    Get-Content $file.FullName >> $outputFile
    "--- END OF FILE ---`n" >> $outputFile
}
Write-Host "All files have been bundled into backend_bundle.txt"