package com.example.ephron.scheldegetijden;

import java.io.Serializable;
import java.util.Calendar;

class SimpleDate implements Serializable
{
    @SuppressWarnings("CanBeFinal")
    int YEAR;
    @SuppressWarnings("CanBeFinal")
    int MONTH;
    @SuppressWarnings("CanBeFinal")
    int DAY;

    SimpleDate(int year, int month, int day)
    {
        YEAR = year;
        MONTH = Math.min(Math.max(month,0),11);
        DAY = Math.min(Math.max(day,1),monthDays(MONTH,YEAR));
    }

    @Override
    public boolean equals(Object other)
    {
        if (other!=null)
        {
            if (other instanceof SimpleDate)
            {
                SimpleDate otherDate = (SimpleDate) other;
                if (this.YEAR == otherDate.YEAR)
                {
                    if (this.MONTH == otherDate.MONTH)
                    {
                        if (this.DAY == otherDate.DAY)
                        {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return (int) ((Math.round(Math.pow(2,this.DAY))%97)
                    * (Math.round(Math.pow(3,this.MONTH))%97)
                    * (Math.round(Math.pow(5,(this.YEAR%10)))%97));
    }

    // will give this-day in days
    int compare(Calendar day)
    {
        int diff = 0;
        int otheryear = day.get(Calendar.YEAR);
        int othermonth = day.get(Calendar.MONTH);
        int otherday = day.get(Calendar.DAY_OF_MONTH);
        if (this.YEAR!=otheryear)
        {
            int start=0, end=0, sign=0;
            if (this.YEAR > otheryear+1)
            {
                start=otheryear+1;
                end=this.YEAR;
                sign = 1;
            } else if (this.YEAR+1 < otheryear)
            {
                start=this.YEAR+1;
                end=otheryear;
                sign = -1;
            }
            for (int i = start; i < end; i++)
            {
                if (isLeapYear(i))
                {
                    diff += sign*366;
                } else
                {
                    diff += sign*365;
                }
            }
        }
        if (this.MONTH!=othermonth || this.YEAR!=otheryear)
        {
            if (this.YEAR!=otheryear)
            {
                int start, end, sign, startyear, endyear;
                if (this.YEAR < otheryear)
                {
                    start=this.MONTH+1;
                    startyear=this.YEAR;
                    end=othermonth;
                    endyear=otheryear;
                    sign = -1;
                } else
                {
                    start=othermonth+1;
                    startyear=otheryear;
                    end=this.MONTH;
                    endyear=this.YEAR;
                    sign= 1;
                }
                for (int i=start; i<12; i++)
                {
                    diff += sign*monthDays(i,startyear);
                }
                for (int i=0; i<end; i++)
                {
                    diff += sign*monthDays(i,endyear);
                }
            } else
            {// same year, different months
                int start=0, end=0, sign=0;
                if (this.MONTH > othermonth)
                {
                    start=othermonth+1;
                    end=this.MONTH;
                    sign = 1;
                } else if (this.MONTH < othermonth)
                {
                    start=this.MONTH+1;
                    end=othermonth;
                    sign = -1;
                }
                for (int i = start; i < end; i++)
                {
                    diff += sign*monthDays(i,this.YEAR);
                }
            }
        }
        if (this.YEAR!=otheryear || this.MONTH!=othermonth)
        {
            if (this.YEAR>otheryear || (this.YEAR==otheryear && this.MONTH>othermonth))
            {
                diff += this.DAY - otherday + monthDays(othermonth,otheryear);
            } else if (this.YEAR<otheryear || (this.YEAR==otheryear && this.MONTH<othermonth))
            {
                diff -= otherday - this.DAY + monthDays(this.MONTH,this.YEAR);
            }
        } else
        {
            diff += this.DAY - otherday;
        }
        return diff;
    }

    private boolean isLeapYear(int year)
    {
        return (year%400==0) || ((year%4==0) && (year%100!=0));
    }

    private int monthDays(int month, int year)
    {
        if (month==1)//February
        {
            if(isLeapYear(year))
            {// leap year
                return 29;
            }
            else
            {
                return 28;
            }
        }
        else
        {
            if (month<7)
            {
                if (month%2==0)
                {//jan, mar, may, jul
                    return 31;
                }
                else
                {// apr, jun
                    return 30;
                }
            }
            else
            {
                if (month%2==0)
                {//sep, nov
                    return 30;
                }
                else
                {// aug, okt, dec
                    return 31;
                }
            }
        }
    }
}
