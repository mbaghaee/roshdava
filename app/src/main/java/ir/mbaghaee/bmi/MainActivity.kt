package ir.mbaghaee.bmi

import android.content.res.ColorStateList
import androidx.activity.OnBackPressedCallback
import androidx.core.content.res.ResourcesCompat
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import ir.mbaghaee.bmi.databinding.*
import ir.mbaghaee.bmi.GrowthScreen as Screen

class MainActivity : AppCompatActivity() {
 private companion object { val historyLock=Any() }
 private val ink by lazy{getColor(R.color.growth_ink)}
 private val teal by lazy{getColor(R.color.growth_teal)}
 private val muted by lazy{getColor(R.color.growth_muted)}
 private val chartViews=mutableListOf<ReferenceChartView>()
 private var chartRecords=JSONArray()
 private val openChartExplorer=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
  if(result.resultCode==RESULT_OK)result.data?.getStringExtra("record")?.let{runCatching{openSavedAssessment(JSONObject(it))}}
 }
 private lateinit var engine:GrowthEngine;private lateinit var body:LinearLayout;private lateinit var scroll:ScrollView
 private lateinit var name:TextInputEditText;private lateinit var weight:TextInputEditText;private lateinit var height:TextInputEditText
 private lateinit var head:TextInputEditText;private lateinit var waist:TextInputEditText
 private lateinit var lying:MaterialCheckBox;private lateinit var premature:MaterialCheckBox;private lateinit var pregnant:MaterialCheckBox
 private lateinit var waistSection:LinearLayout
 private lateinit var headBox:TextInputLayout;private lateinit var waistBox:TextInputLayout;private lateinit var birthButton:MaterialButton;private lateinit var visitButton:MaterialButton
 private lateinit var pregnancyForm:FormPregnancyBinding
 private lateinit var approximateAge:MaterialCheckBox
 private lateinit var approximateAgeBox:TextInputLayout
 private lateinit var approximateAgeInput:TextInputEditText
 private val state=AssessmentUiState()
 private var nutritionCard: NutritionCard? = null
 private var restoredNutrition: JSONObject? = null
 private val navigationButtons=mutableMapOf<Screen,MaterialButton>()
 private var backupBusy=false
 private val createBackupDocument=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)writeBackup(uri)}
 private val openBackupDocument=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)readBackup(uri)}
 private var activePatientId by state::activePatientId
 private var openedProfileId by state::openedProfileId
 private var viewedRecord by state::viewedRecord
 private var resultSnapshot by state::resultSnapshot
 private var resultFromHistory by state::resultFromHistory
 private var editingRecordId by state::editingRecordId
 private var currentScreen:Screen
  get()=state.screen
  set(value){state.screen=value;updateNavigation()}
 private var draft by state::draft
 private val regularFont by lazy { ResourcesCompat.getFont(this,R.font.vazirmatn_regular)!! }
 private val boldFont by lazy { ResourcesCompat.getFont(this,R.font.vazirmatn_bold)!! }
 private var sex by state::sex
 private var birth by state::birth
 private var visit by state::visit
 private var selectedBirth by state::selectedBirth
 private var pregnancyId by state::pregnancyId
 private val prefs by lazy{getSharedPreferences("growth_history",MODE_PRIVATE)}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  engine=GrowthEngine(listOf("bmianthro","lenanthro","weianthro","wfhanthro","wflanthro","hcanthro","bmi2007","hfa2007").associateWith{GrowthEngine.parse(assets.open("$it.csv").bufferedReader().use{r->r.readText()})})
  draft=try{JSONObject(savedInstanceState?.getString("draft")?:prefs.getString("draft","{}")?:"{}")}catch(_:Exception){JSONObject()}
  state.restoreForm(draft)
  editingRecordId=savedInstanceState?.getLong("editingRecordId",0L)?:0L
  shell();form()
  restoredNutrition=savedInstanceState?.getString("nutritionDraft")?.let{runCatching{JSONObject(it)}.getOrNull()}
  when(savedInstanceState?.getString("screen")){
   Screen.BACKUP.name->backupScreen()
   Screen.HISTORY.name->history(savedInstanceState?.getString("profile"))
   Screen.RESULT.name->{
    val historical=savedInstanceState?.getString("viewedRecord")
    if(historical!=null)try{openSavedAssessment(JSONObject(historical))}catch(_:Exception){history()}
    else {
     val snapshot=savedInstanceState?.getString("resultSnapshot")?.let{runCatching{JSONObject(it)}.getOrNull()}
     val restored=snapshot?.let{SavedAssessment.decode(it)}
     if(restored!=null){activePatientId=snapshot?.optString("patientId",activePatientId)?:activePatientId;result(restored.patient,restored.assessment,restored.birthUtc,restored.visitUtc)}
    }
   }
  }
  if(savedInstanceState!=null)scroll.post{scroll.scrollTo(0,savedInstanceState.getInt("scrollY"))}
  onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){if(currentScreen==Screen.FORM&&editingRecordId!=0L)cancelEdit() else if(currentScreen==Screen.RESULT&&resultFromHistory)history(viewedRecord?.optString("patientId")) else if(currentScreen==Screen.HISTORY&&openedProfileId!=null)history() else if(currentScreen!=Screen.FORM)form() else {captureDraft();finish()}}})
 }
 override fun onSaveInstanceState(out:Bundle){
  if(currentScreen==Screen.RESULT)nutritionCard?.let{out.putString("nutritionDraft",it.capture().toString())}
  captureDraft();state.save(out,scroll.scrollY);super.onSaveInstanceState(out)
 }
 override fun onPause(){captureDraft();super.onPause()}
 private fun captureDraft(){
  if(currentScreen!=Screen.FORM||!::name.isInitialized)return
  draft=JSONObject().put("name",name.text.toString()).put("weight",weight.text.toString()).put("height",height.text.toString()).put("head",head.text.toString()).put("waist",waist.text.toString())
   .put("lying",lying.isChecked).put("pre",premature.isChecked).put("preg",pregnant.isChecked).put("waistRestricted",false).put("visit",visit).put("selected",selectedBirth&&!approximateAge.isChecked).put("sex",sex).put("patientId",activePatientId)
   .put("approximateAge",approximateAge.isChecked).put("approximateYears",approximateAgeInput.text.toString())
  if(!approximateAge.isChecked)draft.put("birth",birth)
  draft.put("pregnancyId",pregnancyId).put("prePregnancyWeight",pregnancyForm.preWeightInput.text.toString())
   .put("pregnancyWeek",pregnancyForm.weekInput.text.toString()).put("pregnancyDay",pregnancyForm.dayInput.text.toString()).put("pregnancyPlurality",selectedPlurality()).put("fetusCount",pregnancyForm.fetusCountInput.text.toString())
  storeDraft(draft)
 }
 private fun restoreInputs(){
  name.setText(draft.optString("name"));weight.setText(draft.optString("weight"));height.setText(draft.optString("height"));head.setText(draft.optString("head"));waist.setText(draft.optString("waist"))
  approximateAge.isChecked=draft.optBoolean("approximateAge");approximateAgeInput.setText(draft.optString("approximateYears"))
  pregnancyForm.preWeightInput.setText(draft.optString("prePregnancyWeight"))
  pregnancyForm.weekInput.setText(draft.optString("pregnancyWeek"))
  pregnancyForm.dayInput.setText(draft.optString("pregnancyDay","0"))
  pregnancyForm.fetusCountInput.setText(draft.optString("fetusCount"))
  when(draft.optInt("pregnancyPlurality")){
   1->pregnancyForm.pluralityGroup.check(R.id.single_button)
   2->pregnancyForm.pluralityGroup.check(R.id.twin_button)
   3->pregnancyForm.pluralityGroup.check(R.id.other_button)
   else->pregnancyForm.pluralityGroup.clearChecked()
  }
  lying.isChecked=draft.optBoolean("lying");premature.isChecked=draft.optBoolean("pre");pregnant.isChecked=draft.optBoolean("preg")
 }
 private fun freshAssessment(){
  activePatientId="";pregnancyId="";resultSnapshot=null;draft=JSONObject();storeDraft(null);sex=2;birth=utc(2000,0,1);visit=today();selectedBirth=false;editingRecordId=0L
  currentScreen=Screen.RESULT // Prevent the previous form from restoring its old draft during reset.
  form()
 }
 private fun applyFonts(view:View){
  if(view is TextView)view.typeface=if(view.typeface?.isBold==true)boldFont else regularFont
  if(view is TextInputLayout)view.typeface=regularFont
  if(view is ViewGroup)for(i in 0 until view.childCount)applyFonts(view.getChildAt(i))
 }
 private fun shell(){
  val binding=ActivityMainBinding.inflate(layoutInflater)
  ViewCompat.setOnApplyWindowInsetsListener(binding.root){v,i->
   val bars=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
   v.setPadding(bars.left,bars.top,bars.right,bars.bottom);i
  }
  body=binding.pageBody;scroll=binding.pageScroll
  navigationButtons.clear()
  navigationButtons[Screen.FORM]=binding.formTab
  navigationButtons[Screen.HISTORY]=binding.historyTab
  navigationButtons[Screen.BACKUP]=binding.backupTab
  binding.formTab.setOnClickListener{form()}
  binding.historyTab.setOnClickListener{history()}
  binding.backupTab.setOnClickListener{backupScreen()}
  binding.aboutButton.setOnClickListener{about()}
  setContentView(binding.root);applyFonts(binding.root);updateNavigation()
 }
 private fun updateNavigation(){
  val selected=if(currentScreen==Screen.RESULT){if(resultFromHistory)Screen.HISTORY else Screen.FORM} else currentScreen
  for((screen,item) in navigationButtons){
   val active=screen==selected;item.isSelected=active
   item.backgroundTintList=ColorStateList.valueOf(if(active)teal else Color.WHITE)
   item.setTextColor(if(active)Color.WHITE else ink)
   item.strokeColor=ColorStateList.valueOf(if(active)teal else Color.rgb(183,203,208));item.strokeWidth=dp(1)
   item.typeface=if(active)boldFont else regularFont
   item.contentDescription="${item.text}، ${if(active)"انتخاب شده" else "انتخاب نشده"}"
  }
 }
 override fun dispatchTouchEvent(event:MotionEvent):Boolean {
  if(event.actionMasked==MotionEvent.ACTION_DOWN){
   val location=IntArray(2)
   for(chart in chartViews){chart.getLocationOnScreen(location);val x=event.rawX-location[0];val y=event.rawY-location[1]
    if(x<0||y<0||x>chart.width||y>chart.height)chart.clearSelection()
   }
  }
  return super.dispatchTouchEvent(event)
 }
 private fun bindStatus(view:TextView,text:String,color:Int){
  val genericNormal=text.trim() in setOf("در محدوده مرجع","طبیعی")
  val normal=genericNormal||text==WaistAssessment.NORMAL||text==PregnancyAssessment.WITHIN||text=="وزن پیش از بارداری در محدودهٔ مرجع"
  view.text=if(genericNormal)"در محدوده مرجع (طبیعی)" else text
  view.setTextColor(if(normal)getColor(R.color.growth_normal_text) else color)
  view.setBackgroundResource(if(normal)R.drawable.bg_normal_status else 0)
  view.typeface=boldFont
 }
 private fun statusLabel(text:String,size:Int,color:Int)=label(text,size,color,true).apply{bindStatus(this,text,color)}
 private fun label(t:String,size:Int=14,color:Int=ink,bold:Boolean=false)=ViewGrowthTextBinding.inflate(layoutInflater).root.apply{
  text=t;textSize=size.toFloat();setTextColor(color);typeface=if(bold)boldFont else regularFont
 }
 private fun button(t:String,solid:Boolean=true,action:()->Unit):MaterialButton {
  val view=if(solid)ViewGrowthButtonBinding.inflate(layoutInflater).root else ViewGrowthButtonOutlinedBinding.inflate(layoutInflater).root
  view.text=t;view.setOnClickListener{action()};return view
 }
 private fun gap(parent:LinearLayout=body,h:Int=12){parent.addView(Space(this),LinearLayout.LayoutParams(1,dp(h)))}
 private fun card(parent:LinearLayout=body,fill:Int=Color.WHITE,content:(LinearLayout)->Unit):LinearLayout{
  val binding=ViewGrowthCardBinding.inflate(layoutInflater,parent,false)
  binding.root.setCardBackgroundColor(fill);parent.addView(binding.root)
  content(binding.cardContent);return binding.cardContent
 }
 private fun form(){
  captureDraft();currentScreen=Screen.FORM;body.removeAllViews();chartViews.clear()
  val binding=ScreenAssessmentFormBinding.inflate(layoutInflater,body,true)
  name=binding.patientName;weight=binding.weightInput;height=binding.heightInput
  head=binding.headInput;waist=binding.waistInput
  headBox=binding.headBox;waistBox=binding.waistBox
  waistSection=binding.waistSection
  binding.waistGuideButton.setOnClickListener{waistGuide()}
  lying=binding.lyingCheckbox;premature=binding.prematureCheckbox;pregnant=binding.pregnantCheckbox
  pregnancyForm=binding.pregnancyForm
  pregnant.setOnCheckedChangeListener{_,_->updatePregnancyVisibility()}
  pregnancyForm.pluralityGroup.addOnButtonCheckedListener{_,_,_->stylePlurality()}
  birthButton=binding.birthButton;visitButton=binding.visitButton
  approximateAge=binding.approximateAgeCheckbox;approximateAgeBox=binding.approximateAgeBox;approximateAgeInput=binding.approximateAgeInput
  approximateAge.setOnCheckedChangeListener{_,_->approximateAgeBox.error=null;updateAge()}
  approximateAgeInput.doAfterTextChanged{approximateAgeBox.error=null;updateAge()}
  binding.profileNotice.visibility=if(activePatientId.isBlank())View.GONE else View.VISIBLE
  val group=binding.genderGroup;val girl=binding.girlButton;val boy=binding.boyButton
  fun styleGender(){
   for((b,value,title) in listOf(Triple(girl,2,"دختر / زن"),Triple(boy,1,"پسر / مرد"))){
    val active=sex==value;b.text=if(active)"✓  $title" else title
    b.backgroundTintList=ColorStateList.valueOf(if(active)teal else Color.WHITE)
    b.setTextColor(if(active)Color.WHITE else ink)
    b.strokeColor=ColorStateList.valueOf(if(active)teal else Color.rgb(183,203,208));b.strokeWidth=dp(if(active)2 else 1)
    b.typeface=if(active)boldFont else regularFont
    b.contentDescription="$title، ${if(active)"انتخاب شده" else "انتخاب نشده"}"
   }
  }
  group.check(if(sex==2)girl.id else boy.id)
  group.addOnButtonCheckedListener{_,id,checked->if(checked){sex=if(id==girl.id)2 else 1;styleGender();updateAge()}}
  birthButton.text=if(selectedBirth)"تولد · ${date(birth)}" else "انتخاب تاریخ تولد · شمسی"
  visitButton.text="سنجش · ${date(visit)}"
  birthButton.setOnClickListener{pickDate(true)};visitButton.setOnClickListener{pickDate(false)}
  binding.calculateButton.text=if(editingRecordId!=0L)"ثبت تغییرات و مشاهدهٔ ارزیابی  ←" else "مشاهدهٔ ارزیابی رشد  ←"
  binding.calculateButton.setOnClickListener{calculate()};binding.freshButton.setOnClickListener{freshAssessment()}
  restoreInputs();updateAge();applyFonts(binding.root);styleGender();stylePlurality()
  scroll.post{scroll.scrollTo(0,0)}
 }
 private fun updateAge(){
  if(!::headBox.isInitialized)return
  val approximate=approximateAge.isChecked
  birthButton.visibility=if(approximate)View.GONE else View.VISIBLE
  approximateAgeBox.visibility=if(approximate)View.VISIBLE else View.GONE
  val days=formAgeDays()
  val young=days!=null&&days<1827
  val hasWaist=days!=null&&days>=WaistAssessment.MIN_AGE_DAYS
  headBox.visibility=if(young)View.VISIBLE else View.GONE
  waistBox.visibility=if(hasWaist)View.VISIBLE else View.GONE
  waistSection.visibility=if(hasWaist)View.VISIBLE else View.GONE
  lying.visibility=if(young)View.VISIBLE else View.GONE
  premature.visibility=if(days!=null&&days<731)View.VISIBLE else View.GONE
  pregnant.visibility=if(PregnancyAssessment.canRecord(sex,days))View.VISIBLE else View.GONE
  updatePregnancyVisibility()
 }
 private fun formAgeDays():Int? = if(approximateAge.isChecked)
  AssessmentAge.parseYears(approximateAgeInput.text.toString())?.let(AssessmentAge::approximateDays)
 else if(selectedBirth&&birth<=visit)AssessmentAge.elapsedDays(birth,visit) else null
 private fun updatePregnancyVisibility(){
  if(::pregnancyForm.isInitialized)pregnancyForm.root.visibility=if(pregnant.visibility==View.VISIBLE&&pregnant.isChecked)View.VISIBLE else View.GONE
 }
 private fun selectedPlurality()=when(pregnancyForm.pluralityGroup.checkedButtonId){
  R.id.single_button->1;R.id.twin_button->2;R.id.other_button->3;else->0
 }
 private fun stylePlurality(){
  val selected=selectedPlurality()
  pregnancyForm.fetusCountBox.visibility=if(selected==3)View.VISIBLE else View.GONE
  pregnancyForm.higherOrderNotice.visibility=if(selected==3)View.VISIBLE else View.GONE
  for((button,value,title) in listOf(Triple(pregnancyForm.singleButton,1,"تک‌قلو"),Triple(pregnancyForm.twinButton,2,"دوقلو"),Triple(pregnancyForm.otherButton,3,"بیشتر / نامشخص"))){
   val active=selected==value;button.text=if(active)"✓ $title" else title
   button.backgroundTintList=ColorStateList.valueOf(if(active)teal else Color.WHITE)
   button.setTextColor(if(active)Color.WHITE else ink);button.strokeColor=ColorStateList.valueOf(if(active)teal else getColor(R.color.growth_control_border));button.strokeWidth=dp(1)
   button.typeface=if(active)boldFont else regularFont
   button.contentDescription="$title، ${if(active)"انتخاب شده" else "انتخاب نشده"}"
  }
 }
 private fun newPregnancy(record:JSONObject){
  val saved=SavedAssessment.decode(record)?:LegacyAssessmentReader.read(record,engine)?:return
  val profileId=record.optString("patientId")
  if(profileId.isBlank()||!PregnancyAssessment.canRecord(saved.patient.sex,saved.patient.days))return
  MaterialAlertDialogBuilder(this).setTitle("شروع بارداری بعدی؟")
   .setMessage("فرم برای بارداری دیگری از همین فرد آماده می‌شود؛ وزن پیش از بارداری، هفته و اندازه‌های جدید را باید دوباره وارد کنید. ورودی‌های فرم جاری کنار گذاشته می‌شوند و سوابق ثبت‌شده حفظ می‌شوند.\n\nبرای ثبت سنجش بعدیِ همین بارداری، «سنجش جدید برای همین فرد» را انتخاب کنید.")
   .setNegativeButton("انصراف",null).setPositiveButton("آماده‌سازی فرم بارداری بعدی"){_,_->
    captureDraft();editingRecordId=0L;resultSnapshot=null;viewedRecord=null;resultFromHistory=false
    state.restoreForm(JSONObject().put("patientId",profileId).put("name",saved.patient.name).withSavedAge(saved)
     .put("visit",today()).put("sex",saved.patient.sex).put("preg",true)
     .put("pregnancyId","").put("prePregnancyWeight","").put("pregnancyWeek","").put("pregnancyDay","0").put("pregnancyPlurality",0).put("fetusCount",""))
    storeDraft(draft);currentScreen=Screen.RESULT;form()
   }.show().also{d->d.window?.decorView?.let{applyFonts(it)}}
 }
 private fun readPregnancyInfo():PregnancyInfo? {
  if(pregnant.visibility!=View.VISIBLE||!pregnant.isChecked)return null
  val pre=pregnancyForm.preWeightInput;val week=pregnancyForm.weekInput;val day=pregnancyForm.dayInput
  pre.error=null;week.error=null;day.error=null
  val before=number(pre)
  if(before==null||!before.isFinite()||before !in .3..400.0){pre.error="وزن پیش از بارداری را به کیلوگرم وارد کنید";pre.requestFocus();return null}
  val w=number(week)
  if(w==null||!w.isFinite()||w%1.0!=0.0||w !in 1.0..42.0){week.error="هفتهٔ کامل باید عددی بین ۱ و ۴۲ باشد";week.requestFocus();return null}
  val d=if(day.text.toString().isBlank())0.0 else number(day)
  if(d==null||!d.isFinite()||d%1.0!=0.0||d !in 0.0..6.0){day.error="روز اضافه باید بین ۰ و ۶ باشد";day.requestFocus();return null}
  val plurality=selectedPlurality()
  if(plurality==0){toast("تعداد جنین را انتخاب کنید.");pregnancyForm.pluralityGroup.requestFocus();return null}
  val countInput=pregnancyForm.fetusCountInput;countInput.error=null
  val count=if(plurality==3&&countInput.text.toString().isNotBlank()){
   val value=number(countInput)
   if(value==null||!value.isFinite()||value%1.0!=0.0||value !in 3.0..12.0){countInput.error="تعداد جنین باید عدد کامل بین ۳ و ۱۲ باشد؛ اگر مشخص نیست خالی بگذارید.";countInput.requestFocus();return null}
   value.toInt()
  }else null
  if(pregnancyId.isBlank())pregnancyId=UUID.randomUUID().toString()
  return PregnancyInfo(before,w.toInt()*7+d.toInt(),plurality,pregnancyId,count)
 }
 private fun pregnancyTimingMatches(info:PregnancyInfo):Boolean {
  val previous=HistoryTimeline.forPatient(historyData(),activePatientId)
   .filter{editingRecordId==0L||it.optLong("id")!=editingRecordId}
   .mapNotNull{SavedAssessment.decode(it)}
   .lastOrNull{it.patient.pregnancy?.episodeId==info.episodeId}?:return true
  val expected=previous.patient.pregnancy!!.gestationDays+(visit-previous.visitUtc)/86400000
  if(kotlin.math.abs(info.gestationDays-expected)>14){
   pregnancyForm.weekInput.error="هفته با تاریخ سنجش‌های همین بارداری سازگار نیست؛ تاریخ یا هفته را بازبینی کنید. برای بارداری دیگر، از پروندهٔ فرد «شروع بارداری بعدی» را انتخاب کنید."
   pregnancyForm.weekInput.requestFocus();return false
  }
  return true
 }
 private fun pickDate(isBirth:Boolean){
  val minUtc=if(!isBirth&&selectedBirth&&!approximateAge.isChecked)birth else utc(Calendar.getInstance().get(Calendar.YEAR)-120,0,1)
  val maxUtc=if(isBirth)visit else today()
  if(minUtc>maxUtc){toast("بازه تاریخ نامعتبر است؛ تاریخ سنجش را بررسی کنید.");return}
  val low=SolarDate.fromUtc(minUtc);val high=SolarDate.fromUtc(maxUtc)
  val initial=SolarDate.fromUtc((if(isBirth&&selectedBirth)birth else if(isBirth)visit else visit).coerceIn(minUtc,maxUtc))
  val binding=DialogSolarDateBinding.inflate(layoutInflater)
  val panel=binding.root;val preview=binding.datePreview
  val year=binding.yearPicker;val month=binding.monthPicker;val day=binding.dayPicker
  for(picker in listOf(year,month,day)){
   picker.wrapSelectorWheel=false;picker.setFormatter{SolarDate.persian(it.toString())}
  }
  year.minValue=low.year;year.maxValue=high.year;year.value=initial.year
  month.minValue=1;month.maxValue=12;month.value=initial.month;day.minValue=1;day.maxValue=31;day.value=initial.day
  var adjusting=false
  fun update(){
   if(adjusting)return;adjusting=true
   val y=year.value;val oldMonth=month.value
   val loMonth=if(y==low.year)low.month else 1;val hiMonth=if(y==high.year)high.month else 12
   month.displayedValues=null;month.minValue=loMonth;month.maxValue=hiMonth;month.value=oldMonth.coerceIn(loMonth,hiMonth)
   month.displayedValues=SolarDate.months.copyOfRange(loMonth-1,hiMonth)
   val m=month.value;val oldDay=day.value;val loDay=if(y==low.year&&m==low.month)low.day else 1
   val hiDay=if(y==high.year&&m==high.month)high.day else SolarDate.monthLength(y,m)
   day.minValue=loDay;day.maxValue=hiDay;day.value=oldDay.coerceIn(loDay,hiDay)
   preview.text=SolarDate(y,m,day.value).display()+"  ·  "+SolarDate.months[m-1];adjusting=false
  }
  year.setOnValueChangedListener{_,_,_->update()};month.setOnValueChangedListener{_,_,_->update()};day.setOnValueChangedListener{_,_,_->update()};update()
  val dialog=MaterialAlertDialogBuilder(this).setTitle(if(isBirth)"تاریخ تولد · شمسی" else "تاریخ سنجش · شمسی").setView(panel).setNegativeButton("انصراف",null).setPositiveButton("انتخاب"){_,_->
   val ms=SolarDate(year.value,month.value,day.value).toUtc()
   if(isBirth){birth=ms;selectedBirth=true;birthButton.text="تولد · ${date(ms)}";lying.isChecked=(visit-birth)/86400000<731}
   else{visit=ms;visitButton.text="سنجش · ${date(ms)}"};updateAge();captureDraft()
  }.create()
  dialog.show();applyFonts(dialog.window!!.decorView)
 }
 private fun number(e:TextInputEditText):Double? {var s=e.text.toString().trim().replace('٫','.').replace("٬","");for(i in 0..9){s=s.replace("۰۱۲۳۴۵۶۷۸۹"[i],('0'.code+i).toChar()).replace("٠١٢٣٤٥٦٧٨٩"[i],('0'.code+i).toChar())};return s.toDoubleOrNull()}
 private fun calculate(){
  val days=formAgeDays()
  if(days==null){
   if(approximateAge.isChecked){
    approximateAgeBox.error="سن را به‌صورت عدد صحیح بین ۱۹ و ۱۲۰ سال وارد کنید."
    approximateAgeInput.requestFocus()
   }else toast(if(selectedBirth)"تاریخ تولد نباید بعد از سنجش باشد." else "تاریخ تولد یا سن تقریبی را وارد کنید.")
   return
  }
  val birthAt=if(approximateAge.isChecked)null else birth
  weight.error=null;height.error=null;val w=number(weight);val h=number(height);if(w==null){weight.error="وزن معتبر وارد کنید";weight.requestFocus();return};if(h==null){height.error="قد معتبر وارد کنید";height.requestFocus();return}
  if(h in .25..2.5){height.error="قد را به سانتی‌متر وارد کنید؛ مثلاً ۱٫۷ متر برابر ۱۷۰ سانتی‌متر است.";height.requestFocus();return}
  val hc=if(headBox.visibility==View.VISIBLE&&head.text.toString().isNotBlank())number(head) else null;val wc=if(waistBox.visibility==View.VISIBLE&&waist.text.toString().isNotBlank())number(waist) else null
  if(headBox.visibility==View.VISIBLE&&head.text.toString().isNotBlank()&&hc==null){head.error="عدد معتبر وارد کنید";return};if(waistBox.visibility==View.VISIBLE&&waist.text.toString().isNotBlank()&&wc==null){waist.error="عدد معتبر وارد کنید";return}
  val isPregnant=pregnant.visibility==View.VISIBLE&&pregnant.isChecked
  val pregnancyInfo=readPregnancyInfo()
  if(isPregnant&&pregnancyInfo==null)return
  if(pregnancyInfo!=null&&!pregnancyTimingMatches(pregnancyInfo))return
  val p=Patient(name.text.toString().trim(),sex,days,w,h,lying.visibility==View.VISIBLE&&lying.isChecked,hc,wc,premature.visibility==View.VISIBLE&&premature.isChecked,isPregnant,pregnancy=pregnancyInfo)
  try{
   val prior=HistoryTimeline.forPatient(historyData(),activePatientId).firstNotNullOfOrNull{SavedAssessment.decode(it)}
   if(prior!=null&&!HistoryTimeline.compatible(prior,birthAt,sex)){
    toast(if((prior.birthUtc==null)!=(birthAt==null))"روش ثبت سن را مانند سنجش قبلی این پرونده انتخاب کنید."
     else "تاریخ تولد و جنسیت باید با پرونده یکسان باشند؛ برای فرد دیگر ارزیابی تازه را بزنید.")
    return
   }
   val a=engine.assess(p)
   val visits=HistoryTimeline.forPatient(historyData(),activePatientId).filter{it.optLong("id")!=editingRecordId}.mapNotNull{SavedAssessment.decode(it)}
   val previous=MeasurementReview.previous(SavedAssessment(p,a,birthAt,visit),visits)
   val warnings=MeasurementReview.warnings(p,previous?.patient,previous?.let{AssessmentAge.elapsedDays(it.visitUtc,visit)})
   fun proceed(){
    if(activePatientId.isBlank())activePatientId=UUID.randomUUID().toString()
    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(weight.windowToken,0)
    result(p,if(warnings.isEmpty())a else a.copy(notes=a.notes+warnings.map{"بازبینی اندازه‌گیری: $it"}),birthAt)
   }
   if(warnings.isEmpty())proceed() else MaterialAlertDialogBuilder(this).setTitle("اندازه‌ها نیازمند بازبینی هستند")
    .setMessage(warnings.joinToString("\n\n")+"\n\nاین پیام‌ها فقط احتمال خطای ورود را گوشزد می‌کنند؛ تغییر ممکن است واقعی باشد و تشخیص پزشکی نیست.")
    .setNegativeButton("بازگشت و اصلاح"){_,_->height.requestFocus()}
    .setPositiveButton("اندازه‌ها را بررسی کردم؛ ادامه"){_,_->proceed()}.show().also{d->d.window?.decorView?.let{applyFonts(it)}}
  }catch(e:IllegalArgumentException){toast(e.message?:"ورودی نامعتبر است")}
 }
 private fun result(p:Patient,a:Assessment,birthAt:Long?,visitAt:Long=visit,record:JSONObject?=null){
  captureDraft();resultFromHistory=record!=null;currentScreen=Screen.RESULT;viewedRecord=record
  val report=record?.optString("report")?.takeIf{it.isNotBlank()}?:report(p,a,birthAt,visitAt)
  val stamp=System.currentTimeMillis();val profileId=record?.optString("patientId")?:activePatientId
  resultSnapshot=record?:SavedAssessment(p,a,birthAt,visitAt).encode(stamp,date(visitAt),report,profileId)
  val editing=record==null&&editingRecordId!=0L
  val records=historyData();val matching=HistoryTimeline.matchingMeasurement(records,profileId,p,birthAt,visitAt)
  val shownId=record?.optLong("id")?:matching?.optLong("id")
  // In approximate mode, align charts by actual visit dates, independent of the age entered at each visit.
  val chartAnchor=birthAt?:visitAt-p.days*AssessmentAge.DAY_MS
  val chartVisits=HistoryTimeline.forPatient(records,profileId).mapNotNull{r->
   val id=r.optLong("id")
   if(id==shownId||(editing&&id==editingRecordId))return@mapNotNull null
   val saved=SavedAssessment.decode(r)?.takeIf{HistoryTimeline.compatible(it,birthAt,p.sex)}?:return@mapNotNull null
   val patient=if(birthAt==null)saved.patient.copy(days=AssessmentAge.elapsedDays(chartAnchor,saved.visitUtc)) else saved.patient
   r to ChartVisit(id,patient)
  }
  val priorVisits=chartVisits.map{it.second}
  val currentChartId=shownId?:ChartExplorerData.CURRENT_VISIT_ID
  chartRecords=JSONArray().apply{
   chartVisits.forEach{(r,_)->put(r)}
   val current=record?:matching
   if(current!=null)put(current)
   else resultSnapshot?.let{put(JSONObject(it.toString()).put("explorerCurrent",true))}
  }
  body.removeAllViews();chartViews.clear()
  val binding=ScreenAssessmentResultBinding.inflate(layoutInflater,body,true)
  binding.resultTitle.text=when{editing->"ویرایش سنجش ثبت‌شده";record==null->"نمای روشن از وضعیت امروز";else->"ارزیابی ثبت‌شده"}
  binding.resultSubtitle.text="${p.name.ifBlank{"بدون نام"}}  ·  ${date(visitAt)}"
  val previous=MeasurementReview.previous(SavedAssessment(p,a,birthAt,visitAt),HistoryTimeline.forPatient(records,profileId)
   .filter{it.optLong("id")!=shownId&&!(editing&&it.optLong("id")==editingRecordId)}.mapNotNull{SavedAssessment.decode(it)})
  binding.previousComparison.root.visibility=if(previous==null)View.GONE else View.VISIBLE
  if(previous!=null)binding.previousComparison.comparisonText.text=MeasurementReview.summary(SavedAssessment(p,a,birthAt,visitAt),previous)
  binding.referenceGroup.text=a.group
  binding.bmiValue.text=bmiText(a.pregnancy?.preBmi?:a.bmi,usesAdultBmi(p,a),1)
  binding.bmiLabel.text=when{a.pregnancy!=null->"BMI پیش از بارداری · kg/m²";p.pregnant->"BMI فعلی؛ برای تفسیر بارداری کافی نیست";else->"شاخص توده بدنی · kg/m²"}
  val adultWeightMetric=if(a.adult&&!p.pregnant)a.metrics.firstOrNull{it.title=="وضعیت وزن"&&it.z==null&&it.indicator.isBlank()} else null
  bindStatus(binding.weightStatus,a.pregnancy?.category?:adultWeightMetric?.status?:a.metrics.firstOrNull()?.status?:"",Color.WHITE)
  binding.ageSex.text="${if(p.sex==1)"پسر / مرد" else "دختر / زن"}  ·  ${ageDescription(p.days,birthAt==null)}"
  binding.measurements.text=SolarDate.persian("وزن: ${p.weight} کیلوگرم  ·  قد: ${p.height} سانتی‌متر")
  val preBmi=if(p.pregnant&&p.days>=6575)a.pregnancy?.preBmi else null
  val scaleScore=preBmi?:a.score?.takeUnless{p.pregnant}
  binding.bmiScale.visibility=if(scaleScore==null)View.GONE else View.VISIBLE
  scaleScore?.let{binding.bmiScale.setScore(it,preBmi!=null||a.adult,p.days/30.4375<60)}
  binding.scaleDescription.visibility=if(scaleScore==null&&adultWeightMetric?.detail.isNullOrBlank())View.GONE else View.VISIBLE
  binding.scaleDescription.text=if(preBmi!=null)"نشانگر نوار: BMI پیش از بارداری؛ وزن‌گیری فعلی در کارت بارداری ارزیابی می‌شود." else if(a.adult)adultWeightMetric?.detail?.takeIf{it.isNotBlank()}?:"نشانگر نوار: BMI بر پایه مرزهای بزرگسالان" else if(p.days/30.4375<60)"نشانگر نوار: تناسب وزن با قد، در مقیاس استاندارد" else "نشانگر نوار: BMI نسبت به سن، در مقیاس استاندارد"
  binding.childExplanation.visibility=if(a.adult||p.pregnant)View.GONE else View.VISIBLE
  binding.percentileNotice.visibility=if(a.adult||p.pregnant)View.GONE else View.VISIBLE
  bindPregnancyCard(binding.pregnancyAssessment,p,a,priorVisits,currentChartId,chartAnchor,birthAt==null)
  bindWaistCard(binding.waistAssessment,p,a,priorVisits.map{it.patient})
  val editingSource = if (editing) HistoryTimeline.records(records).firstOrNull {
   it.optLong("id") == editingRecordId && it.optString("patientId") == profileId
  } else null
  val previousNutrition = if (editing) {
   editingSource?.takeIf { nutritionInputsMatch(it, p) }?.optJSONObject("nutrition")
  } else (record ?: matching)?.optJSONObject("nutrition")
  var saveEdited: ((JSONObject?) -> Boolean)? = null
  nutritionCard = NutritionCard(binding.nutritionCard, p, a.adult, restoredNutrition ?: previousNutrition) { plan ->
   if (!p.pregnant) false
   else if (editing) saveEdited?.invoke(plan) == true
   else saveNutritionPlan(record, profileId, p, birthAt, visitAt, plan) {
    SavedAssessment(p,a,birthAt,visitAt).encode(stamp,date(visitAt),report,profileId)
   }.also { saved ->
    if (saved) binding.saveButton.text = "این سنجش قبلاً ثبت شده است"
   }
  }
  restoredNutrition = null
  val adultHeightPlaceholder=if(a.adult)a.metrics.firstOrNull{it.title=="وضعیت قد"&&it.status=="قد ثبت‌شده"&&it.z==null&&it.indicator.isBlank()} else null
  for(m in a.metrics.filterNot{it==adultWeightMetric||it==adultHeightPlaceholder||WaistAssessment.isWaistMetric(it)||it.indicator==PregnancyAssessment.INDICATOR||(p.pregnant&&it.title=="وضعیت وزن")}){
   val item=ItemGrowthMetricBinding.inflate(layoutInflater,binding.metricsContainer,true)
   item.metricTitle.text=m.title
   val color=if(m.severity>=2)Color.rgb(166,60,51) else if(m.severity==1)Color.rgb(155,103,18) else teal
   bindStatus(item.metricStatus,m.status,color)
   val summary=Percentiles.summary(m);item.percentileSummary.text=summary
   item.percentileSummary.visibility=if(summary==null)View.GONE else View.VISIBLE
   item.metricDetail.text=m.detail
   item.zToggle.visibility=if(m.z==null)View.GONE else View.VISIBLE
   m.z?.let{z->
    item.zExplanation.text=Percentiles.explanation(z)
    item.zToggle.setOnClickListener{item.zExplanation.visibility=if(item.zExplanation.visibility==View.GONE)View.VISIBLE else View.GONE}
   }
   val near=engine.plotFor(p,m,visits=priorVisits,visitId=currentChartId)
   item.referenceChart.root.visibility=if(near==null)View.GONE else View.VISIBLE
   if(near!=null){
    bindChart(item.referenceChart,near,chartAnchor)
    item.referenceChart.curveLegend.text="عدد کنار هر خط، صدک مرجع است. خط پررنگ: میانه (۵۰). نقطهٔ پررنگ: این نوبت؛ نقاط دیگر: سنجش‌های همین پرونده."
   item.referenceChart.bandLegend.text=ReferenceBands.legend(m.indicator)
    var full=false
    item.referenceChart.zoomButton.setOnClickListener{
     full=!full;item.referenceChart.chart.setPlot(if(full)engine.plotFor(p,m,true,priorVisits,currentChartId)?:near else near)
     item.referenceChart.zoomButton.text=if(full)"نمای نزدیک کودک" else "نمای کل مرجع"
    }
   }
  }
  binding.trendsTitle.visibility=View.VISIBLE
  val missingTrends=mutableListOf<String>()
  fun showTrend(plot:ReferencePlot,title:String,legend:String){
   if(plot.history.isEmpty()){missingTrends+=title.removePrefix("روند ");return}
   val chart=ItemMeasurementTrendBinding.inflate(layoutInflater,binding.trendsContainer,true).trendChart
   bindChart(chart,if(birthAt==null)AssessmentAge.byVisitDate(plot) else plot,chartAnchor);chart.chartTitle.text=title
   chart.chartTitle.textSize=18f;chart.zoomButton.visibility=View.GONE;chart.bandLegend.visibility=View.GONE
   chart.curveLegend.text=legend
  }
  for(kind in listOf("height","weight")){
   showTrend(engine.measurementTrend(p,kind,priorVisits,currentChartId),if(kind=="height")"روند قد ثبت‌شده" else "روند وزن ثبت‌شده",
    if(birthAt==null)"اندازه‌ها به ترتیب تاریخ سنجش رسم شده‌اند؛ صفر یعنی تاریخ همین سنجش."
     else "این نمودار، اندازه‌های ثبت‌شده را به ترتیب سن نشان می‌دهد؛ خطوط مرجع در کارت‌های بالا هستند.")
  }
  if(p.waist!=null)for(asRatio in listOf(false,true)){
   val plot=WaistAssessment.trend(p,priorVisits,asRatio,currentChartId)?:continue
   showTrend(plot,if(asRatio)"روند نسبت دور کمر به قد" else "روند دور کمر",
    "فقط نوبت‌های دارای دور کمر رسم شده‌اند. این نمودار اندازه‌های ثبت‌شده است؛ تفسیر چربی شکمی را در کارت بخوانید. با لمس نقطه، عدد و تاریخ همان نوبت دیده می‌شود.")
  }
  binding.trendEmptyNotice.visibility=if(missingTrends.isEmpty())View.GONE else View.VISIBLE
  binding.trendEmptyNotice.text=if(binding.trendsContainer.childCount==0)"برای نمایش روند، حداقل یک سنجش دیگر ثبت کنید."
   else "برای نمایش روند ${missingTrends.joinToString(" و ")}، حداقل یک سنجش دیگر با این اندازه‌ها ثبت کنید."
  for(note in a.notes)binding.notesContainer.addView(label(note,13).apply{
   layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)}
  })
  binding.ageReference.text="سن سنجش: ${ageDescription(p.days,birthAt==null)} · "+if(p.pregnant)"راهنمای وزن‌گیری بارداری؛ منابع در کارت" else "مرجع WHO / BMI بزرگسالان"
  binding.saveButton.visibility=if(record==null)View.VISIBLE else View.GONE
  if(editing){
   binding.saveButton.text="ذخیرهٔ تغییرات این سنجش"
   saveEdited = { nutrition ->
    val result=updateHistory{latest->
     val conflict=HistoryTimeline.matchingMeasurement(latest,profileId,p,birthAt,visitAt)?.takeIf{it.optLong("id")!=editingRecordId}
     if(conflict!=null)return@updateHistory null
     val updated=SavedAssessment(p,a,birthAt,visitAt).encode(editingRecordId,date(visitAt),report,profileId)
     (nutrition ?: previousNutrition)?.let { updated.put("nutrition", it) }
     val index=(0 until latest.length()).firstOrNull{i->
      val old=latest.optJSONObject(i)
      old!=null&&old.optLong("id")==editingRecordId&&old.optString("patientId")==profileId
     }
     if(index!=null){
      latest.getJSONObject(index).optString("restoreSourceKey").takeIf{it.isNotBlank()}?.let{updated.put("restoreSourceKey",it)}
      latest.put(index,updated)
     } else latest.put(updated)
     updated to (index!=null)
    }
    if(result==null){
     toast("برای این فرد در همین تاریخ با همین اندازه‌ها سنجش دیگری ثبت شده است؛ تغییرات ذخیره نشد.")
     false
    }else{
     val (updated,wasPresent)=result
     editingRecordId=0L
     toast(if(wasPresent)"تغییرات این سنجش ذخیره شد" else "این سنجش در سوابق نبود؛ به‌عنوان سنجش تازه ذخیره شد.")
     openSavedAssessment(updated)
     true
    }
   }
   binding.saveButton.setOnClickListener{saveEdited?.invoke(null)}
  }else{
   if(matching!=null)binding.saveButton.text="این سنجش قبلاً ثبت شده است"
   binding.saveButton.setOnClickListener{
    val inserted=updateHistory{latest->
     if(HistoryTimeline.matchingMeasurement(latest,profileId,p,birthAt,visitAt)!=null)return@updateHistory false
     latest.put(SavedAssessment(p,a,birthAt,visitAt).encode(stamp,date(visitAt),report,profileId))
     true
    }
    binding.saveButton.text="این سنجش قبلاً ثبت شده است"
    toast(if(inserted)"ارزیابی ذخیره شد" else "برای این فرد در همین تاریخ با همین اندازه‌ها سنجش ثبت شده است.")
   }
  }
  binding.followUpButton.visibility=if(record==null)View.GONE else View.VISIBLE
  binding.profileButton.visibility=if(record==null)View.GONE else View.VISIBLE
  binding.followUpButton.setOnClickListener{if(record!=null)startFollowUp(record)}
  binding.profileButton.setOnClickListener{history(profileId)}
  binding.shareButton.setOnClickListener{share(report)}
  binding.editButton.text=when{editing->"بازگشت به فرم ویرایش";record!=null->"ویرایش این سنجش";else->"بازگشت و ویرایش اطلاعات"}
  binding.editButton.setOnClickListener{when{editing->form();record!=null->startEdit(record);else->form()}}
  binding.freshButton.setOnClickListener{freshAssessment()}
  body.alpha=0f;body.animate().alpha(1f).setDuration(240).start();scroll.post{scroll.scrollTo(0,0)}
 }
 private fun nutritionInputsMatch(record: JSONObject, current: Patient): Boolean {
  val previous = SavedAssessment.decode(record)?.patient ?: return false
  return previous.sex == current.sex && previous.days == current.days &&
   previous.weight == current.weight && previous.height == current.height &&
   previous.pregnant == current.pregnant && previous.premature == current.premature &&
   previous.pregnancy == current.pregnancy
 }

 private fun saveNutritionPlan(
  record: JSONObject?, profileId: String, patient: Patient, birthAt: Long?, visitAt: Long,
  plan: JSONObject, newRecord: () -> JSONObject
 ): Boolean {
  val created=updateHistory{records->
   val existing = if (record == null) {
    HistoryTimeline.matchingMeasurement(records,profileId,patient,birthAt,visitAt)
   } else HistoryTimeline.records(records).firstOrNull {
    it.optLong("id") == record.optLong("id") && it.optString("patientId") == profileId &&
     nutritionInputsMatch(it, patient)
   }
   if(record!=null&&existing==null)return@updateHistory null
   if(existing==null)records.put(newRecord().put("nutrition",plan))
   else existing.put("nutrition",plan)
   existing==null
  }
  if(created==null){
   toast("سنجش تغییر کرده است. دوباره آن را از سوابق باز کنید.")
   return false
  }
  record?.put("nutrition", plan)
  resultSnapshot?.put("nutrition", plan)
  toast(if (created) "سنجش و برنامهٔ تغذیه ثبت شدند" else "برنامهٔ تغذیه در این سنجش ثبت شد")
  return true
 }
 private fun bindPregnancyCard(binding:CardPregnancyAssessmentBinding,p:Patient,a:Assessment,visits:List<ChartVisit>,visitId:Long,chartAnchor:Long,approximate:Boolean){
  binding.root.visibility=if(p.pregnant)View.VISIBLE else View.GONE
  if(!p.pregnant)return
  binding.referenceButton.setOnClickListener{pregnancyReferences(if(p.pregnancy?.plurality==2)a.pregnancy?.preBmi else null,p.pregnancy?.fetusCount)}
  val info=p.pregnancy;val e=a.pregnancy
  if(info==null||e==null){
   binding.gestationLabel.text="اطلاعات تکمیلی بارداری ثبت نشده است"
   binding.baselineLabel.text="در سنجش تازه، وزن پیش از بارداری، هفته و تعداد جنین را تکمیل کنید."
   binding.statusLabel.text="تفسیر وزن‌گیری در دسترس نیست"
   binding.gainValue.visibility=View.GONE;binding.expectedLabel.visibility=View.GONE;binding.gainGauge.visibility=View.GONE
   binding.totalGoalLabel.visibility=View.GONE;binding.preBmiCategory.visibility=View.GONE;binding.detailLabel.visibility=View.GONE
   binding.pregnancyChart.root.visibility=View.GONE;binding.bandMethodNotice.visibility=View.GONE
   return
  }
  binding.gestationLabel.text=PregnancyAssessment.weekLabel(info.gestationDays)+" · "+PregnancyAssessment.pluralityLabel(info.plurality,info.fetusCount)
  binding.baselineLabel.text=SolarDate.persian("وزن پیش از بارداری: ${PregnancyAssessment.n(info.preWeight)} · وزن این نوبت: ${PregnancyAssessment.n(p.weight)} کیلوگرم")
  binding.gainValue.text=SolarDate.persian(PregnancyAssessment.signed(e.gain)+" کیلوگرم")
  bindStatus(binding.statusLabel,e.status,if(e.status==PregnancyAssessment.WITHIN)teal else Color.rgb(155,103,18))
  val twinGuide=if(info.plurality==2&&p.days>=6575&&e.totalLow!=null)TwinPregnancyReference.range(e.preBmi,info.gestationDays) else null
  val higherGuide=if(info.plurality==3&&e.totalLow!=null&&e.totalHigh!=null)e.totalLow to e.totalHigh else null
  binding.higherOrderCaution.visibility=if(info.plurality==3)View.VISIBLE else View.GONE
  binding.higherOrderCaution.text=if(higherGuide!=null)HigherOrderPregnancyReference.NOTICE else "تعیین هدف وزن‌گیری و ارزیابی آن باید به تیم مراقبت بارداری واگذار شود؛ برای تعداد نامشخص یا شش‌قلویی و بیشتر، بازهٔ عمومی تعیین نشده است."
  val weekly=(if(e.weekLow!=null&&e.weekHigh!=null)e.weekLow to e.weekHigh else null)?:twinGuide
  if(weekly!=null){
   val heading=if(twinGuide!=null)"بازهٔ تقریبی نمودار دوقلویی CDC در این هفته" else "افزایش وزن راهنمای این هفته"
   binding.expectedLabel.text="$heading: ${PregnancyAssessment.range(weekly)} کیلوگرم\nوزن راهنمای این هفته: ${PregnancyAssessment.range((info.preWeight+weekly.first) to (info.preWeight+weekly.second))} کیلوگرم"
   binding.expectedLabel.visibility=View.VISIBLE
   binding.gainGauge.visibility=View.VISIBLE;binding.gainGauge.setEvaluation(e.copy(weekLow=weekly.first,weekHigh=weekly.second))
  }else if(higherGuide!=null){
   binding.expectedLabel.text="راهنمای حدودی افزایش وزن کل بارداری: ${PregnancyAssessment.range(higherGuide)} کیلوگرم\nاین نوار پیشرفت به سمت راهنمای کل را نشان می‌دهد؛ هدف این هفته نیست."
   binding.expectedLabel.visibility=View.VISIBLE;binding.gainGauge.visibility=View.VISIBLE
   binding.gainGauge.setTotalReference(e)
  }else{
   binding.gainGauge.visibility=View.GONE
   binding.expectedLabel.text=if(e.status==PregnancyAssessment.FIRST)"راهنمای پایان سه‌ماههٔ اول: حدود ۰٫۵ تا ۲ کیلوگرم افزایش" else ""
   binding.expectedLabel.visibility=if(e.status==PregnancyAssessment.FIRST)View.VISIBLE else View.GONE
  }
  binding.totalGoalLabel.visibility=if(higherGuide!=null)View.GONE else View.VISIBLE
  binding.totalGoalLabel.text=if(e.totalLow!=null&&e.totalHigh!=null)"هدف کلی بارداری: ${PregnancyAssessment.range(e.totalLow to e.totalHigh)} کیلوگرم افزایش" else "هدف کلی باید توسط تیم مراقبت تعیین شود."
  binding.preBmiCategory.text=e.category
  binding.detailLabel.text=if(higherGuide!=null)"منبع: "+HigherOrderPregnancyReference.source(info.fetusCount) else e.guidance.ifBlank{e.detail}
  val plot=PregnancyAssessment.plot(p,e,visits,visitId)
  binding.pregnancyChart.root.visibility=if(plot==null)View.GONE else View.VISIBLE
  binding.bandMethodNotice.visibility=if(plot?.envelope!=null)View.VISIBLE else View.GONE
  if(info.plurality==1&&plot?.envelope!=null){
   val start=plot.envelope.lower.first().x.toInt()
   binding.bandMethodNotice.text=SolarDate.persian("ناحیهٔ رنگی، بازهٔ تقریبی وزن‌گیری از هفتهٔ $start تا ۴۰ است. هدف شخصی با ماما یا پزشک تعیین شود.")
  }
  if(info.plurality==2)binding.bandMethodNotice.text=SolarDate.persian("ناحیهٔ سبز: بازخوانی تقریبی نمودار دوقلویی CDC بر اساس BMI پیش از بارداری؛ هفتهٔ ۵ تا ${TwinPregnancyReference.lastWeek(e.preBmi)}. خارج از این بازه ادامه داده نمی‌شود. هدف شخصی با تیم مراقبت تعیین شود.")
  if(plot!=null){
   val chart=binding.pregnancyChart;bindChart(chart,plot.copy(dateBasedAge=approximate),chartAnchor)
   chart.chartTitle.text="روند وزن‌گیری مادر بر حسب هفتهٔ بارداری"
   chart.zoomButton.visibility=View.GONE;chart.bandLegend.visibility=View.GONE
   chart.curveLegend.text=if(plot.envelope!=null)(if(info.plurality==2)"ناحیهٔ سبز: راهنمای دوقلویی CDC" else "ناحیهٔ سبز: راهنمای برآوردی")+"؛ نقطهٔ پررنگ: این نوبت؛ نقاط دیگر: سنجش‌های همین بارداری. برای دیدن وزن و تاریخ هر نوبت، نقطه را لمس کنید." else "نقاط، وزن‌گیری ثبت‌شدهٔ همین بارداری هستند؛ بازهٔ هفتگی عمومی رسم نشده است. با لمس هر نقطه، عدد و تاریخ آن دیده می‌شود."
  }
 }
 private fun pregnancyReferences(twinBmi:Double?=null,higherCount:Int?=null){
  MaterialAlertDialogBuilder(this).setTitle("مبنای راهنمای وزن‌گیری بارداری")
   .setMessage("هدف کل تک‌قلویی و دوقلویی از BMI پیش از بارداری و راهنمای NAM/IOM و CDC گرفته می‌شود.\n\nناحیهٔ هفتگی تک‌قلویی، برآورد بین بازهٔ شروع سه‌ماههٔ دوم و هدف هفتهٔ ۴۰ است؛ منحنی صدکی یا محدودهٔ تشخیصی نیست. در سه‌ماههٔ اول برچسب کم یا زیادِ هفتگی صادر نمی‌شود.\n\nبرای دوقلویی، ناحیهٔ سبز از بازخوانی تقریبی نمودار اختصاصی CDC در گروه BMI پیش از بارداری گرفته شده است؛ نمودار کمبود وزن تا هفتهٔ ۳۸ و بقیه تا ۴۰ ادامه دارند.\n\nبرای سه‌قلویی، راهنمای حدودی کل از Texas Health و برای چهار و پنج‌قلویی از راهنمای قدیمی POGS 2018 با شواهد پایین گرفته شده است. این اعداد، هدف این هفته یا معیار تشخیص نیستند؛ با BMI شخصی‌سازی نشده‌اند. تعیین هدف نهایی باید به تیم مراقبت بارداری واگذار شود. برای شش‌قلویی و بیشتر یا تعداد نامشخص، بازهٔ خودکار تعیین نمی‌شود. برنامه توصیهٔ کاهش وزن در بارداری نمی‌دهد.")
   .setPositiveButton("مرجع CDC"){_,_->openReference("https://www.cdc.gov/maternal-infant-health/pregnancy-weight/")}
   .setNeutralButton(if(twinBmi!=null)"نمودار مرجع دوقلویی" else if(higherCount!=null&&higherCount in 3..5)"مرجع حدودی چندقلویی" else "مرجع NAM"){_,_->openReference(if(twinBmi!=null)TwinPregnancyReference.sourceUrl(twinBmi) else if(higherCount!=null&&higherCount in 3..5)HigherOrderPregnancyReference.sourceUrl(higherCount) else "https://www.nationalacademies.org/read/12584/chapter/2")}
   .setNegativeButton("بستن",null).show().also{d->d.window?.decorView?.let{applyFonts(it)}}
 }
 private fun bindWaistCard(binding:CardWaistAssessmentBinding,p:Patient,a:Assessment,visits:List<Patient>){
  val ratio=WaistAssessment.ratio(p)
  binding.root.visibility=if(ratio==null&&!p.pregnant)View.GONE else View.VISIBLE
  binding.referenceButton.setOnClickListener{waistReferences()}
  if(p.pregnant){
   binding.waistCardTitle.text="دور کمر در بارداری"
   binding.waistValue.visibility=if(p.waist==null)View.GONE else View.VISIBLE
   p.waist?.let{binding.waistValue.text=SolarDate.persian("دور کمر ثبت‌شده: ${WaistAssessment.number(it)} سانتی‌متر")}
   binding.ratioTitle.visibility=View.GONE;binding.ratioGauge.visibility=View.GONE;binding.waistComparison.visibility=View.GONE
   bindStatus(binding.waistStatus,"تفسیر دور کمر در بارداری انجام نمی‌شود",muted)
   binding.waistDetail.text="به دلیل بارداری، دور کمر و نسبت آن به قد برای ارزیابی چربی شکمی تفسیر نمی‌شوند. برای پیگیری، وزن‌گیری بارداری را در کارت مربوط بررسی کنید."
   binding.waistDetail.visibility=View.VISIBLE;binding.guideButton.visibility=View.GONE
   return
  }
  if(ratio==null)return
  binding.waistValue.text=SolarDate.persian("دور کمر: ${WaistAssessment.number(p.waist!!)} سانتی‌متر · قد: ${WaistAssessment.number(p.height)} سانتی‌متر")
  if(WaistAssessment.outsideAdultRatioReference(p,a.bmi)&&!p.waistRestricted){
   val waist=p.waist!!
   binding.ratioTitle.text="جایگاه دور کمر · مرجع بزرگسالان ایرانی"
   binding.ratioGauge.visibility=View.GONE
   binding.circumferenceGauge.visibility=View.VISIBLE
   binding.circumferenceGauge.setWaist(waist)
   bindStatus(binding.waistStatus,WaistAssessment.iranianWaistPosition(waist),ink)
   binding.waistDetail.text="ناحیهٔ مرجع دور کمر: کمتر از ۹۰ سانتی‌متر. نسبت دور کمر به قد: "+
    SolarDate.persian(WaistAssessment.ratioNumber(ratio).replace('.', '٫'))+
    ". در BMI برابر یا بالاتر از ۳۵، نمودار برای پیگیری اندازه و فاصله از آستانه است؛ خطر کلی را با وضعیت سلامت فرد بسنجید."
   binding.waistDetail.visibility=View.VISIBLE
   binding.waistComparison.text=WaistAssessment.comparison(p,visits)
   binding.guideButton.setOnClickListener{waistGuide()}
   return
  }
  val metric=a.metrics.firstOrNull{it.indicator==WaistAssessment.INDICATOR}
  binding.ratioGauge.setRatio(ratio,metric?.status)
  val color=if(metric?.severity==2)Color.rgb(166,60,51) else if(metric?.severity==1)Color.rgb(155,103,18) else muted
  bindStatus(binding.waistStatus,metric?.status?:"تفسیر ثبت نشده",color)
  val shortDetail=when(metric?.status){
   WaistAssessment.NORMAL->""
   WaistAssessment.INCREASED,WaistAssessment.HIGH->"برای بررسی عوامل خطر با کارشناس سلامت پیگیری شود."
   WaistAssessment.REVIEW->"اندازه‌گیری و وضعیت وزن بازبینی شوند."
   else->metric?.detail?.substringAfter("\n")?.substringBefore("\n")?:"برای این نوبت تفسیر معتبر ثبت نشده است."
  }
  binding.waistDetail.text=shortDetail
  binding.waistDetail.visibility=if(shortDetail.isBlank())View.GONE else View.VISIBLE
  binding.waistComparison.text=WaistAssessment.comparison(p,visits)
  binding.guideButton.setOnClickListener{waistGuide()}
  binding.referenceButton.setOnClickListener{waistReferences()}
 }
 private fun waistGuide(){
  val binding=DialogWaistGuideBinding.inflate(layoutInflater)
  MaterialAlertDialogBuilder(this).setView(binding.root).setPositiveButton("متوجه شدم",null)
   .setNeutralButton("مرجع اندازه‌گیری"){_,_->openReference("https://www.who.int/docs/default-source/ncds/ncd-surveillance/steps/part3-section5.pdf")}
   .show().also{d->d.window?.decorView?.let{applyFonts(it)}}
 }
 private fun waistReferences(){
  MaterialAlertDialogBuilder(this).setTitle("منابع تفسیر دور کمر")
   .setItems(arrayOf("نسبت دور کمر به قد · NICE NG246","مرجع دور کمر بزرگسالان ایرانی","دور کمر در BMI بالا · VA/DoD","روش اندازه‌گیری · WHO STEPS")){_,index->
    openReference(when(index){
     0->"https://www.nice.org.uk/guidance/ng246/chapter/Identifying-and-assessing-overweight-obesity-and-central-adiposity"
     1->"https://pubmed.ncbi.nlm.nih.gov/20433230/"
     2->"https://www.healthquality.va.gov/HEALTHQUALITY/guidelines/CD/obesity/OBE-CPG_2025-Guideline_final_20251105.pdf"
     else->"https://www.who.int/docs/default-source/ncds/ncd-surveillance/steps/part3-section5.pdf"
    })
   }.setNegativeButton("بستن",null).show().also{d->d.window?.decorView?.let{applyFonts(it)}}
 }
 private fun openReference(url:String){try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:Exception){toast("برنامه‌ای برای بازکردن این منبع پیدا نشد.")}}

 private fun bindChart(binding:ViewGrowthChartBinding,plot:ReferencePlot,chartAnchor:Long){
  val needsMore=plot.history.isEmpty()&&plot.curves.isEmpty()&&plot.envelope==null
  binding.singlePointNotice.visibility=if(needsMore)View.VISIBLE else View.GONE
  for(view in listOf(binding.chart,binding.curveLegend,binding.bandLegend,binding.pointCount,binding.zoomButton))view.visibility=if(needsMore)View.GONE else View.VISIBLE
  binding.coordinates.visibility=View.GONE
  binding.chart.setPlot(plot);if(!needsMore)chartViews.add(binding.chart)
  binding.fullscreenButton.visibility=if(needsMore)View.GONE else View.VISIBLE
  binding.fullscreenButton.setOnClickListener{
   binding.fullscreenButton.isEnabled=false
   val displayed=binding.chart.snapshotPlot()
   val payload=JSONObject().put("plot",ChartExplorerData.encode(displayed)).put("chartAnchor",chartAnchor)
    .put("title",binding.chartTitle.text.toString()).put("records",JSONArray(chartRecords.toString()))
    .put("legend",listOf(binding.curveLegend,binding.bandLegend).filter{it.visibility==View.VISIBLE}.joinToString("\n"){it.text.toString()}+
     if(displayed.gestationalWeekAxis)"\nمحدوده‌های بارداری راهنمای تقریبی‌اند؛ هدف نهایی و تفسیر روند باید با تیم مراقبت تعیین شود." else "")
   val filename="chart-${UUID.randomUUID()}.json"
   BackupCodec.io.execute{
    val file=java.io.File(cacheDir,filename)
    try{file.writeText(payload.toString());runOnUiThread{
     binding.fullscreenButton.isEnabled=true
     if(isFinishing||isDestroyed)file.delete() else openChartExplorer.launch(Intent(this,ChartExplorerActivity::class.java).putExtra("file",filename))
    }}catch(_:Exception){file.delete();runOnUiThread{binding.fullscreenButton.isEnabled=true;toast("باز کردن نمودار ممکن نشد؛ دوباره تلاش کنید.")}}
   }
  }
  binding.chart.onPointSelected={point->binding.coordinates.text=chartPointInfo(binding.chart,point,chartAnchor);binding.coordinates.visibility=View.VISIBLE}
  binding.chart.onSelectionCleared={binding.coordinates.visibility=View.GONE}
  binding.pointCount.text=SolarDate.persian("${plot.history.size+1} نقطه؛ برای دیدن مختصات، نقطه را لمس کنید.")
 }
 private fun usesAdultBmi(p:Patient,a:Assessment)=if(p.pregnant)a.pregnancy!=null&&p.days>=6575 else a.adult
 private fun bmiText(value:Double,adult:Boolean,decimals:Int):String {
  val text=if(adult)BmiGaugeRanges.adultNumber(value,decimals) else String.format(Locale.US,"%.${decimals}f",value)
  return SolarDate.persian(text.replace('.','٫'))
 }
 private fun report(p:Patient,a:Assessment,birthAt:Long?,visitAt:Long)=buildString{
  val birthLine=birthAt?.let{"تولد: ${date(it)} (شمسی)\n"}.orEmpty()
  append("گزارش سنجش رشدآوا\n${p.name.ifBlank{"بدون نام"}}\nتاریخ سنجش: ${date(visitAt)}\n${birthLine}سن: ${ageDescription(p.days,birthAt==null)}\nجنسیت: ${if(p.sex==1)"پسر / مرد" else "دختر / زن"}\nوزن: ${p.weight} کیلوگرم | قد ثبت‌شده: ${p.height} سانتی‌متر\nمرجع: ${a.group}\n")
  append("${if(p.pregnant)"BMI فعلی" else "BMI"}: ${bmiText(a.bmi,!p.pregnant&&a.adult,2)}\n")
  p.pregnancy?.let{info->append("بارداری: ${PregnancyAssessment.weekLabel(info.gestationDays)} · ${PregnancyAssessment.pluralityLabel(info.plurality,info.fetusCount)}\nوزن پیش از بارداری: ${info.preWeight} کیلوگرم\n")}
  for(m in a.metrics){
   append("\n${m.title}: ${m.status}\n${m.detail}\n")
   Percentiles.summary(m)?.let{append(it+"\n")};m.z?.let{append(Percentiles.explanation(it)+"\n")}
  }
  append("\n${a.notes.joinToString("\n")}\nغربالگری؛ جایگزین تشخیص و معاینه نیست.")
 }
 private fun storeDraft(value:JSONObject?)=synchronized(historyLock){
  prefs.edit().putString("draft",value?.toString()).apply()
 }
 private fun <T> updateHistory(change:(JSONArray)->T):T=synchronized(historyLock){
  val records=historyData()
  val result=change(records)
  prefs.edit().putString("records",records.toString()).apply()
  result
 }
 private fun removeFromHistory(shouldRemove:(JSONObject)->Boolean)=updateHistory{records->
  for(i in records.length()-1 downTo 0){
   val record=records.optJSONObject(i)?:continue
   if(shouldRemove(record))records.remove(i)
  }
 }
 private fun historyData():JSONArray = synchronized(historyLock){
  val arr=try{JSONArray(prefs.getString("records","[]"))}catch(_:Exception){JSONArray()};var changed=false
  for(i in 0 until arr.length()){
   val record=arr.optJSONObject(i)?:continue
   if(record.optString("patientId").isBlank()){record.put("patientId",UUID.randomUUID().toString());changed=true}
   if(record.optInt("uiSchema")<3){
    val decoded=SavedAssessment.decode(record)
    val saved=decoded?:LegacyAssessmentReader.read(record,engine)
    if(saved!=null){
     val fresh=try{engine.assess(saved.patient)}catch(_:Exception){null}
     val metrics=if(fresh==null)saved.assessment.metrics else saved.assessment.metrics.map{m->
      val match=fresh.metrics.firstOrNull{it.title==m.title}
      if(match!=null&&m.status==match.status)m.copy(indicator=m.indicator.ifBlank{match.indicator},z=m.z?:match.z) else m
     }
     val complete=saved.copy(assessment=saved.assessment.copy(metrics=metrics))
     val migrated=complete.encode(record.optLong("id",System.currentTimeMillis()+i),date(complete.visitUtc),record.optString("report"),record.getString("patientId"))
     if(decoded==null)migrated.put("recoveredFromText",true)
     record.optString("restoreSourceKey").takeIf{it.isNotBlank()}?.let{migrated.put("restoreSourceKey",it)}
     arr.put(i,migrated);changed=true
    }
   }
  }
  if(changed)prefs.edit().putString("records",arr.toString()).apply()
  arr
 }
 private fun history(profileId:String?=null){
  captureDraft();currentScreen=Screen.HISTORY;openedProfileId=profileId;body.removeAllViews();chartViews.clear()
  val binding=ScreenHistoryBinding.inflate(layoutInflater,body,true)
  binding.historyTitle.text=if(profileId==null)"پرونده‌ها و سوابق سنجش" else "سنجش‌های این فرد"
  val arr=historyData()
  if(profileId==null){
   val groups=HistoryTimeline.records(arr).groupBy{HistoryTimeline.profileId(it)}.values
    .sortedByDescending{group->group.maxOf{it.optLong("visitUtc",it.optLong("id"))}}
   binding.emptyCard.visibility=if(groups.isEmpty())View.VISIBLE else View.GONE
   for(group in groups){
    val latest=group.maxBy{it.optLong("visitUtc",it.optLong("id"))}
    val item=ItemPatientProfileBinding.inflate(layoutInflater,binding.recordsContainer,true)
    val profileId=latest.optString("patientId")
    val displayName=latest.optString("name").ifBlank{"بدون نام"}
    item.patientName.text=latest.optString("name")
    item.profileSummary.text=SolarDate.persian("${group.size} سنجش · آخرین نوبت: ${latest.optString("date")}")
    item.deleteProfileButton.contentDescription="حذف کل سوابق $displayName"
    item.deleteProfileButton.setOnClickListener{
     MaterialAlertDialogBuilder(this).setTitle("حذف کل سوابق این فرد؟")
      .setMessage(SolarDate.persian("همهٔ ${group.size} سنجش ثبت‌شده برای «$displayName» حذف می‌شود. این کار قابل بازگشت نیست."))
      .setNegativeButton("انصراف",null).setPositiveButton("حذف همه"){_,_->deleteProfile(profileId);history()}
      .show().also{d->d.window?.decorView?.let{applyFonts(it)}}
    }
    item.openProfileButton.setOnClickListener{history(profileId)}
    item.followUpButton.setOnClickListener{startFollowUp(latest)}
   }
  }else{
   val records=HistoryTimeline.forPatient(arr,profileId)
   binding.emptyCard.visibility=if(records.isEmpty())View.VISIBLE else View.GONE
   records.lastOrNull()?.let{latest->
    binding.profileName.visibility=View.VISIBLE;binding.profileName.text=latest.optString("name")
    binding.followUpButton.visibility=View.VISIBLE;binding.followUpButton.setOnClickListener{startFollowUp(latest)}
    val latestSaved=SavedAssessment.decode(latest)?:LegacyAssessmentReader.read(latest,engine)
    val hasPregnancy=records.any{r->(SavedAssessment.decode(r)?:LegacyAssessmentReader.read(r,engine))?.patient?.pregnant==true}
    if(hasPregnancy&&latestSaved!=null&&PregnancyAssessment.canRecord(latestSaved.patient.sex,latestSaved.patient.days)){
     binding.newPregnancyButton.visibility=View.VISIBLE;binding.newPregnancyButton.setOnClickListener{newPregnancy(latest)}
    }
   }
   for(record in records.asReversed()){
    val item=ItemAssessmentVisitBinding.inflate(layoutInflater,binding.recordsContainer,true)
    item.visitSummary.text=record.optString("date")+" · "+record.optString("status")
    item.openAssessmentButton.setOnClickListener{openSavedAssessment(record)}
    item.deleteButton.setOnClickListener{
     MaterialAlertDialogBuilder(this).setTitle("حذف این سنجش؟").setMessage("فقط این نوبت از پرونده حذف می‌شود.")
      .setNegativeButton("انصراف",null).setPositiveButton("حذف"){_,_->
       removeFromHistory{it.optLong("id")==record.optLong("id")&&it.optString("patientId")==profileId}
       history(profileId)
      }.show().also{d->d.window?.decorView?.let{applyFonts(it)}}
    }
   }
   binding.allProfilesButton.visibility=View.VISIBLE;binding.allProfilesButton.setOnClickListener{history()}
  }
  scroll.post{scroll.scrollTo(0,0)}
 }
 private fun deleteProfile(profileId:String){
  removeFromHistory{it.optString("patientId")==profileId}
  if(activePatientId==profileId){
   activePatientId="";pregnancyId="";editingRecordId=0L
   draft.put("patientId","").put("pregnancyId","")
   storeDraft(draft)
  }
  toast("کل سوابق این فرد حذف شد")
 }
 private fun startFollowUp(record:JSONObject){
  val saved=SavedAssessment.decode(record)?:LegacyAssessmentReader.read(record,engine)
  captureDraft();editingRecordId=0L;activePatientId=record.optString("patientId");visit=today();sex=saved?.patient?.sex?:2
  val oldPregnancy=saved?.patient?.pregnancy
  val elapsed=saved?.let{(visit-it.visitUtc)/86400000}
  val continued=if(oldPregnancy!=null&&elapsed!=null)PregnancyAssessment.advance(oldPregnancy,elapsed) else null
  val carryPregnant=saved?.patient?.pregnant==true&&(oldPregnancy==null||continued!=null)
  pregnancyId=continued?.episodeId?:""
  draft=JSONObject().put("patientId",activePatientId).put("name",saved?.patient?.name?:record.optString("name")).withSavedAge(saved).put("visit",visit).put("sex",sex)
   .put("lying",saved?.birthUtc?.let{AssessmentAge.elapsedDays(it,visit)<731}==true).put("pre",saved?.patient?.premature?:false).put("preg",carryPregnant).put("waistRestricted",false)
   .put("pregnancyId",pregnancyId).put("prePregnancyWeight",continued?.preWeight?.toString()?:"").put("pregnancyWeek",continued?.let{(it.gestationDays/7).toString()}?:"")
   .put("pregnancyDay",continued?.let{(it.gestationDays%7).toString()}?:"0").put("pregnancyPlurality",continued?.plurality?:0).put("fetusCount",continued?.fetusCount?.toString()?:"")
  state.restoreForm(draft)
  storeDraft(draft);currentScreen=Screen.RESULT;form()
  if(saved==null)toast("تاریخ تولد و جنسیت این پرونده قدیمی را تکمیل کنید؛ اندازه‌های جدید را وارد کنید.")
 }
 private fun JSONObject.withSavedAge(saved:SavedAssessment?):JSONObject {
  val actualBirth=saved?.birthUtc
  if(actualBirth!=null)return put("selected",true).put("birth",actualBirth)
  if(saved!=null)put("approximateAge",true).put("approximateYears",AssessmentAge.approximateYears(saved.patient.days).toString())
  return put("selected",false)
 }
 private fun plainNumber(value:Double)=if(value%1.0==0.0)value.toInt().toString() else value.toString()
 private fun startEdit(record:JSONObject){
  val saved=SavedAssessment.decode(record)?:return
  captureDraft();activePatientId=record.optString("patientId");editingRecordId=record.optLong("id")
  visit=saved.visitUtc;sex=saved.patient.sex
  val p=saved.patient;val info=p.pregnancy
  pregnancyId=info?.episodeId?:""
  draft=JSONObject().put("patientId",activePatientId).put("name",p.name)
   .put("weight",plainNumber(p.weight)).put("height",plainNumber(p.height))
   .put("head",p.head?.let{plainNumber(it)}?:"").put("waist",p.waist?.let{plainNumber(it)}?:"")
   .put("lying",p.lying).put("pre",p.premature).put("preg",p.pregnant).put("waistRestricted",p.waistRestricted)
   .withSavedAge(saved).put("visit",visit).put("sex",sex)
   .put("pregnancyId",pregnancyId).put("prePregnancyWeight",info?.let{plainNumber(it.preWeight)}?:"")
   .put("pregnancyWeek",info?.let{(it.gestationDays/7).toString()}?:"")
   .put("pregnancyDay",info?.let{(it.gestationDays%7).toString()}?:"0")
   .put("pregnancyPlurality",info?.plurality?:0).put("fetusCount",info?.fetusCount?.toString()?:"")
  state.restoreForm(draft)
  storeDraft(draft);currentScreen=Screen.RESULT;form()
  toast("اطلاعات این سنجش در فرم بارگذاری شد؛ پس از تغییر، دوباره محاسبه کنید.")
 }
 private fun cancelEdit(){
  val id=editingRecordId;editingRecordId=0L
  val record=HistoryTimeline.records(historyData()).firstOrNull{it.optLong("id")==id&&it.optString("patientId")==activePatientId}
  if(record!=null)openSavedAssessment(record) else form()
 }
 private fun chartPointInfo(chart:ReferenceChartView,point:PlotPoint,birthAt:Long)=chart.describePoint(point)+"\nتاریخ سنجش: "+date(birthAt+point.day*86400000L)
 private fun backupScreen(){
  captureDraft();currentScreen=Screen.BACKUP;body.removeAllViews();chartViews.clear()
  val binding=ScreenBackupBinding.inflate(layoutInflater,body,true);val records=historyData()
  binding.backupSummary.text=SolarDate.persian("${BackupCodec.profileCount(records)} پرونده · ${records.length()} سنجش ذخیره‌شده")
  binding.exportButton.setOnClickListener{
   if(!backupBusy)try{createBackupDocument.launch("roshdava-${date(today()).replace('/','-')}.json")}catch(_:Exception){toast("انتخاب محل ذخیره ممکن نشد.")}
  }
  binding.importButton.setOnClickListener{
   if(!backupBusy)try{openBackupDocument.launch(arrayOf("application/json","text/plain","application/octet-stream"))}catch(_:Exception){toast("بازکردن انتخابگر فایل ممکن نشد.")}
  }
  binding.busyLabel.visibility=if(backupBusy)View.VISIBLE else View.GONE
  binding.exportButton.isEnabled=!backupBusy;binding.importButton.isEnabled=!backupBusy
  scroll.post{scroll.scrollTo(0,0)}
 }
 private fun <T> backupTask(task:()->T,done:(T)->Unit){
  if(backupBusy){toast("یک عملیات فایل در حال انجام است.");return};backupBusy=true
  if(currentScreen==Screen.BACKUP)backupScreen()
  BackupCodec.io.execute{
   try{val value=task();runOnUiThread{if(!isDestroyed&&!isFinishing){backupBusy=false;done(value)}}}
   catch(e:Exception){runOnUiThread{if(!isDestroyed&&!isFinishing){backupBusy=false;if(currentScreen==Screen.BACKUP)backupScreen();toast(if(e is BackupException)e.message?:"فایل معتبر نیست." else "عملیات فایل انجام نشد؛ دسترسی و فضای ذخیره را بررسی کنید.")}}}
  }
 }
 private fun writeBackup(uri:Uri){
  captureDraft();val records=historyData().toString();val form=draft.toString()
  backupTask({
   val text=BackupCodec.encode(JSONArray(records),JSONObject(form))
   val output=contentResolver.openOutputStream(uri,"wt")?:throw BackupException("دسترسی نوشتن به فایل ممکن نیست.")
   output.use{it.write(text.toByteArray(Charsets.UTF_8));it.flush()}
  }){if(currentScreen==Screen.BACKUP)backupScreen();toast("فایل پشتیبان ذخیره شد.")}
 }
 private fun readBackup(uri:Uri){
  captureDraft();val existing=historyData().toString()
  backupTask({
   val input=contentResolver.openInputStream(uri)?:throw BackupException("دسترسی خواندن فایل ممکن نیست.")
   val data=input.use{BackupCodec.read(it)};val plan=BackupCodec.merge(JSONArray(existing),data.records);data to plan
  }){(data,plan)->showRestorePreview(data,plan)}
 }
 private fun showRestorePreview(data:BackupData,plan:RestorePlan){
  val binding=DialogRestoreBackupBinding.inflate(layoutInflater);val panel=binding.root
  binding.restoreSummary.text=SolarDate.persian("در فایل: ${BackupCodec.profileCount(data.records)} پرونده و ${data.records.length()} سنجش\nسنجش جدید: ${plan.added}\nتکراری: ${plan.duplicates}\nتعارض شناسه با حفظ هر دو سنجش: ${plan.preservedConflicts}")
  val form=binding.restoreFormCheckbox;form.isEnabled=data.draft.length()>0
  val dialog=MaterialAlertDialogBuilder(this).setTitle("بازیابی این پشتیبان؟").setView(panel).setNegativeButton("انصراف",null).setPositiveButton("افزودن اطلاعات"){_,_->
   val restoreForm=form.isChecked
   historyData() // Normalize legacy data before merging, as in regular history reads.
   val expectedDraft=synchronized(historyLock){prefs.getString("draft",null)}
   val replacementDraft=data.draft.toString().takeIf{restoreForm}
   backupTask({
    BackupCodec.mergeLatest(data.records,
     readSnapshot={synchronized(historyLock){prefs.getString("records","[]")?:"[]"}},
     commitIfCurrent={snapshot,merged->
      synchronized(historyLock){
       val latest=prefs.getString("records","[]")?:"[]"
       if(latest!=snapshot) null else {
        val oldDraft=prefs.getString("draft",null)
        val replaceDraft=replacementDraft!=null&&oldDraft==expectedDraft
        val edit=prefs.edit().putString("records",merged.records.toString())
        if(replaceDraft)edit.putString("draft",replacementDraft)
        if(!edit.commit()){
         val rollback=prefs.edit().putString("records",snapshot)
         if(replaceDraft)rollback.putString("draft",oldDraft)
         rollback.commit()
         throw BackupException("ذخیرهٔ بازیابی ناموفق بود؛ فضای دستگاه را بررسی کنید.")
        }
        replaceDraft
       }
      }
     })
   }){(merged,draftRestored)->
    val onForm=currentScreen==Screen.FORM
    if(onForm)captureDraft() // Give newer live input priority over the restored draft.
    val useDraft=draftRestored&&!onForm&&prefs.getString("draft",null)==replacementDraft
    if(useDraft)state.restoreForm(JSONObject(requireNotNull(replacementDraft)))
    if(currentScreen==Screen.BACKUP)backupScreen()
    val draftNotice=if(restoreForm&&!useDraft)"\nورودی جاری فرم حفظ شد." else ""
    toast(SolarDate.persian("بازیابی انجام شد؛ ${merged.added} سنجش اضافه و ${merged.duplicates} تکراری رد شد."+draftNotice))
   }
  }.create();dialog.show();applyFonts(dialog.window!!.decorView)
 }
 private fun about(){MaterialAlertDialogBuilder(this).setTitle("رشدآوا · مرجع محاسبات").setMessage("\n\nتولد تا پنج سال: WHO 2006؛ قد و وزن نسبت به سن و وزن نسبت به قد.\nاز پنج تا پیش از ۱۹ سال؛ پل مرجع ۶۰ تا ۶۱ ماه: WHO 2007 با درون‌یابی ماهانه.\nاز ۱۹ سال: طبقه‌بندی BMI بزرگسالان.\n\nبرای شیرخوار، وزن نسبت به طول بدن شاخص اصلی است. قد خوابیده/ایستاده با اصلاح ۰٫۷ سانتی‌متر تطبیق می‌یابد. بیرون ±۳، اصلاح دنباله WHO اعمال می‌شود.\n\n مراجع:\nwho.int/tools/child-growth-standards\nwho.int/tools/growth-reference-data-for-5to19-years\ncdc.gov/bmi\n\nنارس بودن و بارداری به ارزیابی اختصاصی نیاز دارند. نسخه 1.2  \nmbaghaee@gmail.com بقایی   ").setPositiveButton("متوجه شدم",null).show().also{d->d.window?.decorView?.let{applyFonts(it)}}}
 private fun share(text:String){startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"اشتراک گزارش"))}
 private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_LONG).show()
 private fun utc(y:Int,m:Int,d:Int)=Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply{clear();set(y,m,d)}.timeInMillis
 private fun today():Long{val c=Calendar.getInstance();return utc(c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH))}
 private fun date(ms:Long)=SolarDate.fromUtc(ms).display()
 private fun ageDescription(days:Int,approximate:Boolean=false):String {
  if(approximate)return SolarDate.persian("حدود ${AssessmentAge.approximateYears(days)} سال")
  val months=days/30.4375
  return SolarDate.persian(if(months>=228)"${(months/12).toInt()} سال" else if(days<31)"$days روز" else if(months<24)"${months.toInt()} ماه" else "${(months/12).toInt()} سال و ${(months%12).toInt()} ماه")
 }
 private fun openSavedAssessment(record:JSONObject){
  val saved=SavedAssessment.decode(record)
  if(saved!=null){
   val withIndicators=try{val fresh=engine.assess(saved.patient);saved.assessment.copy(metrics=saved.assessment.metrics.map{m->val current=fresh.metrics.firstOrNull{it.title==m.title};if(current!=null&&m.status==current.status)m.copy(indicator=m.indicator.ifBlank{current.indicator},z=m.z?:current.z) else m})}catch(_:Exception){saved.assessment}
   result(saved.patient,withIndicators,saved.birthUtc,saved.visitUtc,record);return
  }
  // Historical v1 reports contained only text; show the original report without guessing clinical inputs.
  captureDraft();resultFromHistory=true;currentScreen=Screen.RESULT;viewedRecord=record;resultSnapshot=null;nutritionCard=null;restoredNutrition=null;body.removeAllViews();chartViews.clear()
  body.addView(label("ارزیابی ثبت‌شده",24,ink,true));gap();body.addView(label(record.optString("name")+" · "+record.optString("date"),14,muted));gap()
  card(fill=Color.rgb(19,65,76)){v->v.addView(statusLabel(record.optString("status"),22,Color.WHITE));v.addView(label("گزارش نسخهٔ قبلی",12,Color.rgb(209,230,232)))}
  card{v->v.addView(label("گزارش کامل ثبت‌شده",17,ink,true));gap(v);v.addView(label(record.optString("report"),14,ink).apply{setTextIsSelectable(true)})}
  card(fill=Color.rgb(235,242,244)){v->v.addView(label("این سابقه فقط به‌صورت متن ذخیره شده است؛ اطلاعات کافی برای بازسازی مطمئن نمودارها وجود ندارد. سوابقی که از این نسخه ثبت شوند با همهٔ کارت‌ها و نمودارها باز می‌شوند.",13,muted))}
  body.addView(button("سنجش جدید برای همین فرد",true){startFollowUp(record)},LinearLayout.LayoutParams(-1,dp(56)))
  body.addView(button("بازگشت به سوابق سنجش",true){history(record.optString("patientId"))},LinearLayout.LayoutParams(-1,dp(56)))
  body.addView(button("اشتراک‌گذاری متن گزارش",false){share(record.optString("report"))},LinearLayout.LayoutParams(-1,dp(56)))
  scroll.post{scroll.scrollTo(0,0)}
 }
}
