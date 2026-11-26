# NAT V2.5 experimental source
ฐาน V2.4: เพิ่มการจับรูปแบบ 12 กม. · 02:27 น. · 75% โดยเฉพาะ, เปิดการตัดบรรทัดใน TextView นำทาง, ใช้สีจาก ColorScheme และเพิ่ม log วินิจฉัยการเปลี่ยนธีม ไม่เปลี่ยนการคำนวณแบตเตอรี่หรือจุดชาร์จ
ข้อจำกัด: ยังไม่ได้ compile หรือทดสอบบนรถ; ถ้าหน้านำทางไม่ใช่ TextView หรือ parent จำกัดความสูง อาจยังไม่แสดงการ์ด ต้องตรวจ runtime เพิ่ม การสลับธีมทั้งแผนที่ยังไม่ยืนยันว่าแก้ได้
Build: แตก ZIP, นำ source ไปยัง repo NAT เดิม (รวม .github), commit/push, Actions > NAT V2.5 integrated patch build > Run workflow. ดาวน์โหลด artifact MPP เมื่อสำเร็จ, เปิด Morphe Sources > Local เลือก MPP ใหม่ ปิดตัวเก่า, Apps > APK ต้นฉบับ 26.10.120003.E > Select patches > NAT arrival battery V2.5 experimental > Patch. เก็บ APK V2.4 ไว้ก่อนติดตั้งทดสอบขณะจอดรถ
Log: logcat -d -s NatArrivalUi:I MorpheColorScheme:I AndroidRuntime:E
