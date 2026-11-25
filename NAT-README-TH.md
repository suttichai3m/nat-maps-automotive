# NAT Maps Automotive — ต้นแบบทดลอง
ต่อยอดจาก ZIP ต้นฉบับ 0.2.0 (รักษา LICENSE/NOTICE เดิม) ไม่ใช่ APK สำเร็จรูป
เพิ่ม NatArrivalUi.java และ NatArrivalUiPatch.kt: ขยายเฉพาะตัวเลขของข้อความ Arrive with 41% และข้อความไทยที่ตรงรูปแบบ 1.65 เท่าและตัวหนา โดยไม่แก้ค่าคำนวณ ใช้สีเดิมของแอป ไม่เพิ่มกรอบหรือ progress bar ในรุ่นทดลองนี้ หาก Google Maps วาดข้อความด้วยระบบอื่นที่ไม่ใช่ TextView จะไม่เกิดผล ต้องตรวจสอบบนรถจริง
Patch ปิดโดยค่าเริ่มต้น ต้องเลือก NAT arrival battery (experimental) ใน Morphe Desktop 2.2.0 ขึ้นไป ใช้ APK ต้นฉบับที่ยังไม่ patch เวอร์ชัน 26.10.120003.E ไม่ควร patch ซ้ำบน APK 0.1.9
## Build
ใช้ JDK 21, Android SDK 36, Gradle wrapper และ GitHub Packages credentials ตาม docs/build-setup.md ตั้งค่า gpr.user/gpr.key ใน ~/.gradle/gradle.properties (ไม่ส่ง token ให้ผู้อื่น) แล้วเปิด NAT-Build.command บน Mac หรือใช้ bash NAT-Build.command ใน Terminal หาก Finder ไม่อนุญาต
ทางเลือก: อัปโหลดโปรเจกต์ไป GitHub ของตน เปิด Actions > NAT experimental patch build > Run workflow หาก token เริ่มต้นอ่าน packages ไม่ได้ ให้ตั้ง repository secret MORPHE_PACKAGES_TOKEN ด้วย token read:packages ที่ได้รับอนุญาต (อาจต้องปรับ username ให้ตรงเจ้าของ token) ดาวน์โหลด artifact .mpp เมื่อ build สำเร็จ
## APK และการทดสอบ
เปิด Morphe Desktop เลือก APK ต้นฉบับที่ยังไม่ patch และ bundle ที่ build แล้ว เลือก patch NAT และ patch ที่จำเป็นตามต้นฉบับ ใช้ระบบลงลายเซ็นของ Morphe เก็บ APK เดิมและ signing key ไว้ อย่าฝืนติดตั้งทับหากลายเซ็นไม่ตรง ทดสอบขณะรถจอด: เริ่มนำทาง ตรวจค่าก่อน/หลัง เปลี่ยนเส้นทาง กลางวัน/กลางคืน ภาษาไทย/อังกฤษ ตรวจว่าตัวเลขไม่ถูกตัด และไม่มี crash หากไม่ผ่านให้กลับไปใช้ APK เดิม
## สถานะ
ยังไม่ผ่านการ compile, build, sign หรือทดสอบ Android/BYD เนื่องจากระบบนี้เข้าถึง Gradle server ไม่ได้และไม่มี SDK ไม่มีการรับประกันว่าต้นแบบจะแสดงผลบน Maps จริง

## V2 experimental
เพิ่ม ReplacementSpan สำหรับกรอบตัวเลข 1.65 เท่าและแถบระดับ 0–100% สีแดงต่ำกว่า 20% สีส้มต่ำกว่า 35% นอกนั้นสีเขียว รองรับสีตาม Android UI mode. ใช้กับ TextView ที่ตรงกับข้อความ arrival เท่านั้น; อาจไม่ทำงานกับ renderer อื่นหรือพื้นที่จำกัด. ยังไม่ได้ compile หรือทดสอบ Android. ผลการลอง Gradle อยู่ใน NAT-BUILD-LOG.txt. ไม่มี APK หรือ MPP ใหม่ใน ZIP นี้.
