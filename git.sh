echo "# application-overlay" > README.md
git init
git add README.md
git add .
git commit -m "first commit"
git branch -M main
git remote add origin git@github.com:dcorby/application-overlay.git
git push -u origin main
