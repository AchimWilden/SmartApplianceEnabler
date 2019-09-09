import {AfterViewChecked, Component, Input, OnInit} from '@angular/core';
import {ControlContainer, FormGroup, FormGroupDirective, Validators} from '@angular/forms';
import {Logger} from '../log/logger';
import {TranslateService} from '@ngx-translate/core';
import {ErrorMessageHandler} from '../shared/error-message-handler';
import {FormHandler} from '../shared/form-handler';
import {ErrorMessages} from '../shared/error-messages';
import {ErrorMessage, ValidatorType} from '../shared/error-message';
import {InputValidatorPatterns} from '../shared/input-validator-patterns';
import {ModbusRead} from './modbus-read';
import {ModbusReadValue} from '../modbus-read-value/modbus-read-value';

@Component({
  selector: 'app-modbus-read',
  templateUrl: './modbus-read.component.html',
  styleUrls: ['../global.css'],
  viewProviders: [
    {provide: ControlContainer, useExisting: FormGroupDirective}
  ]
})
export class ModbusReadComponent implements OnInit, AfterViewChecked {
  @Input()
  modbusRead: ModbusRead;
  @Input()
  valueNames: string[];
  @Input()
  readRegisterTypes: string[];
  @Input()
  formControlNamePrefix = '';
  @Input()
  singleValue = false;
  form: FormGroup;
  formHandler: FormHandler;
  @Input()
  translationPrefix: string;
  @Input()
  translationKeys: string[];
  errors: { [key: string]: string } = {};
  errorMessages: ErrorMessages;
  errorMessageHandler: ErrorMessageHandler;

  constructor(private logger: Logger,
              private parent: FormGroupDirective,
              private translate: TranslateService
  ) {
    this.errorMessageHandler = new ErrorMessageHandler(logger);
    this.formHandler = new FormHandler();
  }

  ngOnInit() {
    this.errorMessages = new ErrorMessages('ModbusReadComponent.error.', [
      new ErrorMessage(this.getFormControlName('address'), ValidatorType.required, 'address'),
      new ErrorMessage(this.getFormControlName('address'), ValidatorType.pattern, 'address'),
      new ErrorMessage(this.getFormControlName('bytes'), ValidatorType.pattern, 'bytes'),
      new ErrorMessage(this.getFormControlName('factorToValue'), ValidatorType.pattern, 'factorToValue'),
    ], this.translate);
    this.form = this.parent.form;
    this.expandParentForm(this.form, this.modbusRead, this.formHandler);
    this.form.statusChanges.subscribe(() => {
      this.errors = this.errorMessageHandler.applyErrorMessages4ReactiveForm(this.form, this.errorMessages);
      console.log('ERRORS=', this.errors);
    });
    // this.translate.get(this.translationKeys).subscribe(translatedStrings => {
    //   this.translatedStrings = translatedStrings;
    // });
  }

  ngAfterViewChecked() {
    this.formHandler.markLabelsRequired();
  }

  getFormControlName(formControlName: string): string {
    return `${this.formControlNamePrefix}${formControlName.charAt(0).toUpperCase()}${formControlName.slice(1)}`;
  }

  getReadValueFormControlPrefix(index: number) {
    return `${this.formControlNamePrefix}readValue${index}.`;
  }

  get valueName() {
    // TODO ist das so notwendig?
    if (this.modbusRead.registerReadValues && this.modbusRead.registerReadValues.length === 1) {
      const modbusReadValue = this.modbusRead.registerReadValues[0];
      // if (modbusReadValue.name) {
      //   return this.getTranslatedValueName(modbusReadValue.name);
      // }
    }
    return undefined;
  }

  get type(): string {
    const typeControl = this.form.controls[this.getFormControlName('type')];
    return (typeControl ? typeControl.value : '');
  }

  // TODO move to config
  getByteOrders(): string[] {
    return ['BigEndian', 'LittleEndian'];
  }

  addValue() {
    const newReadValue = new ModbusReadValue();
    if (!this.modbusRead.registerReadValues) {
      this.modbusRead.registerReadValues = [];
    }
    this.modbusRead.registerReadValues.push(newReadValue);
    this.form.markAsDirty();
  }

  removeValue(index: number) {
    this.modbusRead.registerReadValues.splice(index, 1);
    this.form.markAsDirty();
  }

  expandParentForm(form: FormGroup, modbusRead: ModbusRead, formHandler: FormHandler) {
    formHandler.addFormControl(form, this.getFormControlName('address'),
      modbusRead ? modbusRead.address : undefined,
      [Validators.required, Validators.pattern(InputValidatorPatterns.INTEGER_OR_HEX)]);
    formHandler.addFormControl(form, this.getFormControlName('type'),
      modbusRead ? modbusRead.type : undefined,
      [Validators.required]);
    formHandler.addFormControl(form, this.getFormControlName('bytes'),
      modbusRead ? modbusRead.bytes : undefined,
      [Validators.pattern(InputValidatorPatterns.INTEGER)]);
    formHandler.addFormControl(form, this.getFormControlName('byteOrder'),
      modbusRead ? modbusRead.byteOrder : undefined);
    formHandler.addFormControl(form, this.getFormControlName('factorToValue'),
      modbusRead ? modbusRead.factorToValue : undefined,
      [Validators.pattern(InputValidatorPatterns.FLOAT)]);
  }
}
