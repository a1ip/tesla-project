# Установка и использование #
  1. скачать архив с исходниками на ruby
  1. cкачать jar с генератором
  1. скачать и установить Jruby http://www.jruby.org
  1. скачать и установить инструмент Z3 http://research.microsoft.com/en-us/um/redmond/projects/z3/download.html
  1. подготовить модель микропроцессора
  1. подготовить набор тестовых шаблонов (или подготовить генератор тестовых шаблонов)
  1. подготовить конструктор текстов тестовых программ
  1. подготовить и исполнить процедуру, вызывающую генератор из jar

## Подготовить модель микропроцессора ##
Модель микропроцессора состоит из модели блоков MMU (например, TLB, отдельный уровень кэш-памяти и т.п.) и моделей инструкций. Модели инструкций задаются в виде XML-текста (см. [TestSituationsXMLDescription](TestSituationsXMLDescription.md)). Модели блоков оформляются в виде класса, реализующего интерфейс `ru.teslaprj.Microprocessor`.

## Подготовить набор тестовых шаблонов ##
Тестовые шаблоны задаются в виде XML-текста (см. [TestTemplatesXMLDescription](TestTemplatesXMLDescription.md)).

## Подготовить конструктор текстов тестовых программ ##
Надо реализовать интерфейс `ru.teslaprj.TextConstructor`. Он состоит из всего одного метода, который по данному решению, шаблону и количеству иницализирующих обращений в блокм должен построить инициализирующую программу:
```
	ru.teslaprj.Program build_initialization(
			Map<ru.teslaprj.Parameter, BigInteger> solve,
			Map<ru.teslaprj.Table, Integer> initlengths,
			ru.teslaprj.Template template );
```
Инициализация состоит из последовательности инструкций, которые осуществляют:
  * инициализацию состояния микропроцессора, установкой нужных флагов и регистров состояния
  * инициализация регистров, установкой в них нужных значений
  * инициализация блоков MMU последовательностью инструкций обращения к памяти, которые обращаются именно к этим блокам

## Подготовить и исполнить процедуру, вызывающую генератор из jar ##
Типичная процедура вызова генератора имеет следующий вид (`microprocessor` -- модель микропроцессора, `template` -- тестовый шаблон, `ThisTextConstructor` -- класс-конструктор текстов):
```
  // ИНИЦИАЛИЗАЦИЯ ГЕНЕРАТОРА
    	ru.teslaprj.Generator smt_generator = new ru.teslaprj.Generator();

        smt_generator.setMicroprocessor( microprocessor );
    	smt_generator.setSolver( new ru.teslaprj.Solver() );
    	smt_generator.setTextConstructor( new ThisTextConstructor() );

        set_environment( smt_generator )

  // ЗАПУСК ГЕНЕРАТОРА    	   	
    	try
    	{
    		return smt_generator.generate( template, false );
    	}
    	catch(Exception e)
    	{
    		e.printStackTrace();
    	}
```

Обязательная последовательность действий по работе с генератором следующая:
  1. инициализация
    * `setMicroprocessor` --- установить модель микропроцессора, с которой будет работать генератор
    * `setSolver` --- установить решатель, с которым будет работать генератор
    * `setTextConstructor` --- установить конструктор текстов программ, с которым будет работать генератор
    * задать расположение jruby и исходников ruby, с которыми будет работать генератор; нужно задать следующие параметры:
      * `setJrubyHome` --- домащняя директория для Jruby, например, C:/Program Files/jruby-1.4.0
      * `setJrubyLib` --- директория для библиоетек Jruby, например, C:/Program Files/jruby-1.4.0/lib
      * `setJrubyScript` --- скрипт для запуска Jruby, например, C:/Program Files/jruby-1.4.0/bin/jruby.bat
      * `setJrubyShell` --- оболочка для запуска в ней Jruby, например, C:\WINDOWS\system32\cmd.exe
      * `setPathToRubySources` --- директория с исходниками ruby, непосредственно в ней должны располагаться файлы c расширением `rb` (из скаченного zip-архива)
  1. запуск --- вызов метода `generate`; первый параметр -- тестовый шаблон, второй --- необходимость поиска более коротких цепочек инструкций, инициализирующих блоки MMU (при этом время генерации шаблона увеличивается !).

Следующий код читает значение необходимых параметров из переменных окружения и передает их в генератор:
```
    	String jruby_home =  System.getProperty( "jruby.home" );
    	if ( jruby_home == null )
    		throw new IllegalStateException("Isn't defined environment variable JRUBY_HOME");    	
    	smt_generator.setJrubyHome( jruby_home );

    	String jruby_lib = System.getProperty("jruby.lib");
    	if ( jruby_lib == null )
    		throw new IllegalStateException("Isn't defined environment variable JRUBY_LIB");    	
    	smt_generator.setJrubyLib( jruby_lib );
    	
    	String jruby_script = System.getProperty("jruby.script");
    	if ( jruby_script == null )
    		throw new IllegalStateException("Isn't defined environment variable JRUBY_SCRIPT");    	
    	smt_generator.setJrubyScript( jruby_script );
    	
    	String jruby_shell = System.getProperty("jruby.shell");
    	if ( jruby_shell == null )
    		throw new IllegalStateException("Isn't defined environment variable JRUBY_SHELL");    	
    	smt_generator.setJrubyShell( jruby_shell );
    	
    	String tesla_ruby = System.getProperty("tesla.ruby");
    	if ( tesla_ruby == null )
    		throw new IllegalStateException("Isn't defined environment variable TESLA_RUBY");    	
    	smt_generator.setPathToRubySources( tesla_ruby );
```