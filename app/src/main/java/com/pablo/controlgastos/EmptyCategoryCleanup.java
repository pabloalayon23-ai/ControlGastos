package com.pablo.controlgastos;

import android.content.Context;
import android.database.Cursor;
import java.util.*;

public final class EmptyCategoryCleanup {
    private EmptyCategoryCleanup(){}

    public static ArrayList<String> findEmptyLast12Months(Context c){
        LinkedHashSet<String> configured=new LinkedHashSet<>(DetectionRules.categoryNames(c));
        configured.removeIf(x->x==null||x.trim().isEmpty()||x.equalsIgnoreCase("Otros"));
        HashSet<String> used=new HashSet<>();
        ExpenseDb db=new ExpenseDb(c);
        Calendar from=Calendar.getInstance();
        from.add(Calendar.MONTH,-12);
        Cursor q=db.getReadableDatabase().rawQuery("SELECT DISTINCT category FROM tx WHERE type='GASTO' AND ts>=?",new String[]{String.valueOf(from.getTimeInMillis())});
        try{while(q.moveToNext()){String cat=q.getString(0);if(cat!=null&&!cat.trim().isEmpty())used.add(cat.trim().toLowerCase(Locale.ROOT));}}finally{q.close();}
        ArrayList<String> out=new ArrayList<>();
        for(String cat:configured)if(!used.contains(cat.trim().toLowerCase(Locale.ROOT)))out.add(cat);
        Collections.sort(out,String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static int removeCategories(Context c,Collection<String> categories){
        if(categories==null||categories.isEmpty())return 0;
        HashSet<String> remove=new HashSet<>();
        for(String s:categories)if(s!=null&&!s.trim().isEmpty()&&!s.equalsIgnoreCase("Otros"))remove.add(s.trim().toLowerCase(Locale.ROOT));
        if(remove.isEmpty())return 0;

        ArrayList<DetectionRules.Rule> rules=DetectionRules.load(c);
        ArrayList<String> words=new ArrayList<>(),cats=new ArrayList<>();
        for(DetectionRules.Rule r:rules){
            if(remove.contains((r.category==null?"":r.category.trim().toLowerCase(Locale.ROOT))))continue;
            words.add(r.word);cats.add(r.category);
        }
        DetectionRules.save(c,words,cats);

        LinkedHashSet<String> registry=CategoryPrefs.registry(c);
        registry.removeIf(x->remove.contains(x.trim().toLowerCase(Locale.ROOT)));
        CategoryPrefs.replaceRegistry(c,registry);
        return remove.size();
    }
}
