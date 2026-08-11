# VoiceAssist

Voice command se phone control karne wali Android app (Google Assistant jaisa) —
Accessibility Service ke through home jaana, wapas jaana, app kholna, scroll karna, waghera.

## ⚠️ Zaroori samajh

Ye app **Accessibility Service** use karti hai — matlab install karne ke baad aapko
khud jaakar **Settings → Accessibility → VoiceAssist → On** karna padega. Ye Android
ki security policy hai, koi app khud-ba-khud ye permission nahi le sakti.

## Termux se GitHub par push karna (build ke liye)

1. Termux mein git install karein (agar nahi hai):
   ```
   pkg install git
   ```

2. Is `VoiceAssist` folder ke andar jaakar git repo banayein:
   ```
   cd VoiceAssist
   git init
   git add .
   git commit -m "VoiceAssist first version"
   ```

3. GitHub par ek naya **empty repository** banayein (browser se, github.com par,
   naam "VoiceAssist" rakhein, README/gitignore mat add karein).

4. Us repo ko Termux se connect karein (GITHUB_USERNAME apna naam daalein):
   ```
   git remote add origin https://github.com/GITHUB_USERNAME/VoiceAssist.git
   git branch -M main
   git push -u origin main
   ```
   (Push karte waqt GitHub username aur ek **Personal Access Token** maangega
   password ki jagah — token GitHub Settings → Developer Settings →
   Personal Access Tokens se bana sakte hain.)

## APK kaise milega

1. Push hone ke baad GitHub par apne repo mein jaakar **Actions** tab kholein.
2. "Build APK" workflow apne aap chalega (2-4 minute lagte hain).
3. Workflow complete hone par uske andar **Artifacts** section mein
   "VoiceAssist-debug-apk" milega — download kar lein.
4. Zip file phone mein download karke usmein se `.apk` file nikal kar install kar lein
   (Settings mein "Unknown apps install" allow karna padega).

## App mein kya bol sakte hain

- "home jao" — home screen par jaata hai
- "wapas jao" — peeche jaata hai
- "recent apps" — recent apps dikhata hai
- "notifications kholo" — notification panel kholta hai
- "scroll neeche" / "scroll upar" — screen scroll karta hai
- "screenshot lo" — screenshot leta hai (Android 9+)
- "WhatsApp kholo" (ya kisi bhi installed app ka naam) — us app ko khol deta hai

### Kisi bhi khuli hui app ke andar control (naya)

- "dabao \<naam\>" ya "click \<naam\>" — us naam wale button/text par click karta hai
  (jaise "dabao Send", "dabao Like", "dabao Camera") — kisi bhi app ke andar kaam karta hai,
  bashart us button ka koi readable label ho
- "likho \<text\>" ya "type \<text\>" — jo text box currently selected/focused hai
  usmein bola gaya text daal deta hai

**Limit:** kuch apps apne buttons ko readable label nahi dete (custom-drawn UI),
un mein ye commands kaam nahi karenge — ye Android ki hi limitation hai,
Google Assistant bhi wahi issue face karta hai.

## Future improvements (khud add kar sakte hain)

- Zyada commands (jaise "volume badhao", "flashlight on")
- Continuous listening (baar-baar mic button dabana na pade)
- English + Hindi mix better handle karna
- Screen par dikh rahe sabhi clickable items ko number dekar bolna
  (jaise "1 dabao") — un apps ke liye jinke labels nahi hote
